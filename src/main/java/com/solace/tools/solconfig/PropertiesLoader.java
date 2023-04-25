package com.solace.tools.solconfig;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class PropertiesLoader {

    public static Properties loadProperties(String propertyFileName) {
        Properties properties = new Properties();
        try {
            InputStream inputStream = PropertiesLoader.class
                    .getClassLoader()
                    .getResourceAsStream(propertyFileName);
            properties.load(inputStream);
            inputStream.close();
        } catch (IOException e) {
            log.error("Error when loading properties file: " + propertyFileName);
        }
        return properties;
    }
}
