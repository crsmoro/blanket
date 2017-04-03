package com.shuffle.blanket;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.vfs2.FileNotFoundException;

import com.shuffle.sfnetworkscan.SfNetworkScan;
import com.shuffle.sfnetworkscan.SfNetworkScanListener;
import com.shuffle.sfreleaseguess.ReleaseType;
import com.shuffle.sfreleaseguess.SfReleaseGuess;
import com.shuffle.turtleget.Download;
import com.shuffle.turtleget.TurtleGet;
import com.shuffle.turtleget.TurtleGet.StartType;
import com.shuffle.turtleget.TurtleGetListener;
import com.shuffle.wicker.RuTorrentClient;
import com.shuffle.wicker.Torrent;
import com.shuffle.wicker.Wicker;

public class Blanket {

	private final static transient Log log = LogFactory.getLog(Blanket.class);

	private Properties configProperties;

	private ScheduledExecutorService scanSeedboxExecutor = Executors.newSingleThreadScheduledExecutor();

	private volatile SfNetworkScan sfNetworkScan;

	private SfReleaseGuess sfReleaseGuess;

	private ScanSeedbox scanSeedbox;

	private String sourceUrl;

	private String label;

	private String seedboxUrl;

	private String seedboxUsername;

	private String seedboxPassword;

	private String destinationPathOther;

	private String destinationPathMovie;

	@SuppressWarnings("unused")
	private String destinationPathTvshow;

	private TurtleGet turtleGet;

	private String mailHostname;

	private String mailPort;

	private boolean mailSSL;

	private boolean mailTLS;

	private String mailUsername;

	private String mailPassword;

	private String mailFrom;

	private String mailFromName;

	private String mailSendTo;

	private boolean sendMail;
	
	private List<String> torrentsAdded = new ArrayList<>();

	private void setupDeviceManager() {
		log.info("Starting Device Manager");
		if (StringUtils.isNotBlank(configProperties.getProperty("network.scan.ips"))) {
			sfNetworkScan = SfNetworkScan.getInstance(Long.valueOf(configProperties.getOrDefault("network.scan.interval", "900").toString()));
			sfNetworkScan.setScanTimeout((Integer.valueOf(configProperties.getOrDefault("network.scan.timeout", "2").toString())) * 1000);
			for (String device : configProperties.getProperty("network.scan.ips").split(";")) {
				sfNetworkScan.addDevice(device);
			}
			sfNetworkScan.setListener(new SfNetworkScanListener() {

				@Override
				public void onInactive(String device) {
					log.debug(device);
				}

				@Override
				public void onActive(String device) {
					log.debug(device);
					turtleGet.pauseAll();
				}

				@Override
				public void onAllInactive() {
					log.debug("allInactive");
					turtleGet.startAll();
				}

				@Override
				public void onAllActive() {

				}
			});
		}
		else {
			log.info("No IPs to scan");
		}
		log.info("Finished starting Device Manager");
	}

	private void setupDownloadManager() {
		log.info("Starting Download Manager");
		turtleGet = new TurtleGet(System.getProperty("turtle.data") != null ? new File(System.getProperty("turtle.data")) : null);
		turtleGet.addListener(new TurtleGetListener() {

			@Override
			public void initialized() {
				if (sfNetworkScan.isAllInactive()) {
					turtleGet.startAll();
				}
			}

			@Override
			public void started(Download download) {

			}

			@Override
			public void progress(Download download) {

			}

			@Override
			public void paused(Download download) {

			}

			@Override
			public void finished(Download download) {

				if (sendMail && StringUtils.isNotBlank(mailHostname) && StringUtils.isNotBlank(mailSendTo)) {
					try {
						HtmlEmail email = new HtmlEmail();
						email.setHostName(mailHostname);
						email.setSmtpPort(Integer.valueOf(mailPort));
						email.setAuthenticator(new DefaultAuthenticator(mailUsername, mailPassword));
						email.setSSLOnConnect(mailSSL);
						if (mailSSL) {
							email.setSslSmtpPort(String.valueOf(mailPort));
						}
						email.setStartTLSEnabled(mailTLS);
						email.setFrom(mailFrom, mailFromName);
						email.setSubject("Blanket - Download Finished");
						email.setHtmlMsg(mountHtmlMail(download));
						email.addTo(mailSendTo.split(";"));
						email.send();
					} catch (EmailException e) {
						log.error("Error when trying to send mail", e);
					}
				}
			}

			@Override
			public void error(Download download, Exception exception) {
				final Exception e = exception;
				Thread errorThread = new Thread(new Runnable() {
					
					@Override
					public void run() {
						//TODO turtleget properties
						log.error("Something went wrong", e);
						log.info("Running error thread");
						log.info("Trying to connect again");
						boolean ok = false;
						//TODO attempts get from properties
						for (int i=1; i<=5 && !ok; i++) {
							try {
								//TODO get from properties
								Thread.sleep(60000);
							}
							catch (Exception e) {
								
							}
							log.info("Attempt " + i);
							try {
								download.getSource().getContent().getInputStream().read(new byte[1]);
								ok = true;
							}
							catch (FileNotFoundException e) {
								log.error("File doesnt exists anymore, removing from queue", e);
								turtleGet.removeDownload(download);
								//TODO attempts get from properties
								i = 6;
							}
							catch (IOException e) {
								log.error("Nope", e);
							}
						}
						if (ok) {
							log.info("All ok, starting all again");
							download.schedule();
						}
						else {
							log.info("Not ok, trying to start others download");
						}
						if (sfNetworkScan.isAllInactive()) {
							turtleGet.start();
						}
					}
				});
				errorThread.setDaemon(true);
				errorThread.start();
			}
		});
		log.info("Finished starting Download Manager");
	}

	private String mountHtmlMail(Download download) {
		return download.getDestination().getName().getBaseName() + " was finished with success to " + download.getDestination() + "!";
	}

	private void setupReleaseGuess() {
		log.info("Starting Release Guess");
		sfReleaseGuess = new SfReleaseGuess(configProperties.getProperty("themoviedb.apikey"));
		log.info("Finished starting Release Guess");
	}

	private void setupSeedboxVariables() {
		log.info("Starting setup seedbox variables");
		label = configProperties.getProperty("seedbox.label");
		seedboxUrl = configProperties.getProperty("seedbox.url");
		seedboxUsername = configProperties.getProperty("seedbox.username");
		seedboxPassword = configProperties.getProperty("seedbox.password");
		destinationPathOther = configProperties.getProperty("destination.path.other");
		destinationPathMovie = configProperties.getProperty("destination.path.movie");
		destinationPathTvshow = configProperties.getProperty("destination.path.tvshow");
		log.info("Finished setup seedbox variables");
	}

	private void setupMailVariables() {
		log.info("Starting setup mail variables");
		mailHostname = configProperties.getProperty("mail.config.hostname");
		mailPort = configProperties.getProperty("mail.config.port");
		mailSSL = Boolean.valueOf(configProperties.getProperty("mail.config.ssl"));
		mailTLS = Boolean.valueOf(configProperties.getProperty("mail.config.tls"));
		mailUsername = configProperties.getProperty("mail.config.username");
		mailPassword = configProperties.getProperty("mail.config.password");
		mailFrom = configProperties.getProperty("mail.config.from");
		mailFromName = configProperties.getProperty("mail.config.fromname");
		mailSendTo = configProperties.getProperty("mail.send.to");
		sendMail = Boolean.valueOf(configProperties.getProperty("mail.send.download.finish"));
		log.info("Finished setup mail variables");
	}

	private void setupVariables() {
		log.info("Starting setup variables");
		sourceUrl = configProperties.getProperty("source.url");
		setupSeedboxVariables();
		setupMailVariables();
		log.info("Finished setup variables");
	}

	private void setupScanSeedboxExecutor() {
		log.info("Starting setup ScanSeedbox Executor");
		scanSeedbox = new ScanSeedbox();
		scanSeedboxExecutor.scheduleWithFixedDelay(scanSeedbox, 30, Long.valueOf(this.configProperties.getOrDefault("scan.seedbox.interval", 2200).toString()), TimeUnit.SECONDS);
		log.info("Finished setup ScanSeedbox Executor");
	}

	public Blanket(Properties configProperties) {
		log.info("Starting Blanket instance");
		this.configProperties = configProperties;
		log.debug("configProperties loaded " + this.configProperties);
		setupVariables();
		setupReleaseGuess();
		setupDownloadManager();
		setupDeviceManager();
		setupScanSeedboxExecutor();
		log.info("Finished starting Blanket instance");
	}

	private class ScanSeedbox implements Runnable {

		@Override
		public void run() {
			log.info("Started");
			try {
				boolean startNow = false;
				Wicker ruTorrent = new RuTorrentClient(seedboxUrl, seedboxUsername, seedboxPassword);
				String downloadFolder = ruTorrent.getDownloadFolder();

				log.info("Searching torrents");
				log.debug(torrentsAdded);
				log.debug(turtleGet.getQueue());
				for (Torrent torrent : ruTorrent.getTorrents()) {
					//FIXME colocar tipo de comparação do label
					if ((StringUtils.isBlank(label) || torrent.getLabel().contains(label)) && torrent.isFinished() && !torrentsAdded.contains(torrent.getId())) {
						String destinationPath = destinationPathOther;
						log.info(torrent);
						torrentsAdded.add(torrent.getId());
						String sourcePath = sourceUrl + torrent.getPath().replace(downloadFolder, "");
						log.info("Adding Torrent to download");
						if (sfReleaseGuess.getReleaseType(torrent.getName()).equals(ReleaseType.MOVIE)) {
							destinationPath = destinationPathMovie;
						}
						log.debug("sourcePath : " + sourcePath);
						startNow = sfNetworkScan.isAllInactive();
						log.trace("DEVICE isAllInactive : " + startNow);
						log.trace("start : " + (startNow ? "now" : "later"));
						turtleGet.addDownload(sourcePath, destinationPath, startNow ? StartType.AUTOMATICALLY : StartType.MANUALLY);
					}
				}
				log.info("Finished searching torrents");
			} catch (Exception e) {
				log.error("Something went wrong", e);
			}
			log.info("Finished");
		}

	}
}
