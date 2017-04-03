package com.shuffle.blanket;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

public class BlanketMain {

	private static transient final Log log = LogFactory.getLog(BlanketMain.class);

	public static void main(String[] args) {
		initLogger();
		Logger.getRootLogger().setLevel(Level.toLevel(System.getProperty("log", "WARN")));
		
		Logger.getLogger("com.shuffle").setLevel(Level.INFO);
		//Logger.getLogger("com.shuffle.blanket.Blanket").setLevel(Level.DEBUG);
		//Logger.getLogger("com.shuffle.turtleget").setLevel(Level.INFO);
		
		
		Date start = new Date();
		log.info("Starting...");
		
		new Blanket(loadConfigProperties());
		
		Date finish = new Date();
		log.info("Finished startup");
		log.info("Took " + (finish.getTime() - start.getTime()) + " ms");
	}

	private static Properties loadConfigProperties() {
		
		Properties configProperties = new Properties();
		File configFile = new File(System.getProperty("config.properties", System.getProperty("user.home") + File.separator + ".blanket" + File.separator + "config.properties"));
		InputStream configInputStream = null;

		try {
			try {
				if (configFile.exists()) {
					configInputStream = new FileInputStream(configFile);
					configProperties.load(configInputStream);
				}
			} finally {
				if (configFile.exists()) {
					configInputStream.close();
				}
			}
		} catch (IOException e) {
			log.error("Error loading config.properties, shutting down app", e);
			System.exit(-1);
		}
		return configProperties;
	}
	
	private static void initLogger() {
		String logFile = System.getProperty("log.file");
		if (logFile != null) {
			// This is the root logger provided by log4j
			Logger rootLogger = Logger.getRootLogger();

			// Define log pattern layout
			PatternLayout layout = new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n");

			try {
				// Define file appender with layout and output log file name
				RollingFileAppender fileAppender = new RollingFileAppender(layout, logFile);
				fileAppender.setImmediateFlush(true);
				fileAppender.setThreshold(Level.toLevel(System.getProperty("log", "WARN")));
				fileAppender.setAppend(true);
				fileAppender.setMaxFileSize("1MB");
				fileAppender.setMaxBackupIndex(5);

				// Add the appender to root logger
				rootLogger.addAppender(fileAppender);
			} catch (IOException e) {
				System.out.println("Failed to add appender !!");
				System.exit(-1);
			}
		}
		
		
	}
}
