package com.wavefront.labs.convert;

import com.wavefront.rest.models.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class Convert {

	private static final Logger logger = LogManager.getLogger(Convert.class);

	private Properties properties;

	public static void main(String[] args) {

		Convert convert = new Convert();
		convert.start(args);

	}

	public void start(String[] args) {
		logger.info("Convert to Wavefront starting...");

		try {
			properties = new Properties();
			properties.load(new FileReader(new File(args[0])));

			List models = doConvert(args);

			doWrite(models);

		} catch (IOException | InstantiationException | ClassNotFoundException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
			logger.error("Fatal error in start.", e);
		}

		logger.info("Convert to Wavefront finished!");
		logger.error(com.wavefront.labs.convert.utils.Tracker.map);
	}

	private List doConvert(String[] args) throws ClassNotFoundException, IllegalAccessException, InstantiationException, IOException, NoSuchMethodException, InvocationTargetException {
		logger.info("Start Conversion");

		Converter converter = (Converter) Class.forName(properties.getProperty("convert.converter")).getDeclaredConstructor().newInstance();
		converter.init(properties);

		String filename = null;
		if (args.length > 1) {
			filename = args[1];
		} else {
			filename = properties.getProperty("convert.file");
		}

		if (filename == null || filename.equals("")) {
			logger.info("No file/path to convert specified.");
			converter.parse(null);
		} else {
			logger.info("Find file/path to convert: " + filename);
			File file = new File(filename);
			if (file.isDirectory()) {
				List<Path> paths = Files.list(file.toPath()).collect(Collectors.toList());
				for (Path path : paths) {
					File _file = path.toFile();
					if (!_file.isDirectory()) {
						logger.info("Converting file: " + _file.getName());
						converter.parse(_file);
					}
				}
			} else {
				logger.info("Converting file: " + file.getName());
				converter.parse(file);
			}
		}

		return converter.convert();
	}

	private void doWrite(List models) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
		logger.info("Start Writing");

		String generatorName = properties.getProperty("convert.writer", "com.wavefront.labs.convert.writer.WavefrontPublisher");
		Writer writer = (Writer) Class.forName(generatorName).getDeclaredConstructor().newInstance();
		writer.init(properties);

		// tags can be separated by whitespace, comma, or semi-colon
		List<String> tags = Arrays.asList(properties.getProperty("convert.writer.tags", "").split("(\\s|,|;)"));

		for (Object model : models) {
			if (model instanceof Dashboard) {
				Dashboard dashboard = (Dashboard) model;
				logger.info("Writing Dashboard: " + dashboard.getName());

				if (dashboard.getTags() == null) {
					dashboard.setTags(new WFTags());
				}
				addTags(dashboard.getTags(), tags);
				writer.writeDashboard(dashboard);

			} else if (model instanceof Alert) {
				Alert alert = (Alert) model;
				logger.info("Writing Alert: " + alert.getName());

				if (alert.getTags() == null) {
					alert.setTags(new WFTags());
				}
				addTags(alert.getTags(), tags);
				writer.writeAlert(alert);

			} else if (model instanceof MaintenanceWindow) {
				MaintenanceWindow maintenanceWindow = (MaintenanceWindow) model;
				logger.info("Writing Maintenance Window: " + maintenanceWindow.getTitle());

				for (String tag : tags) {
					maintenanceWindow.addRelevantCustomerTagsItem(tag);
				}
				writer.writeMaintenanceWindow(maintenanceWindow);

			} else if (model instanceof UserToCreate) {
				UserToCreate userToCreate = (UserToCreate) model;
				logger.info("Writing User: " + userToCreate.getEmailAddress());

				writer.writeUser(userToCreate);

			} else {
				logger.error("Invalid model class: " + model.getClass().getName());
			}
		}
	}

	private void addTags(WFTags wfTags, List<String> tags) {
		for (String tag : tags) {
			wfTags.addCustomerTagsItem(tag);
		}
	}
}
