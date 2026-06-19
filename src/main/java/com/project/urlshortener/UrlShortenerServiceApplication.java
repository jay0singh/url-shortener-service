package com.project.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class UrlShortenerServiceApplication {

	public static void main(String[] args) {
		// JVMs on Windows can resolve the local zone to a deprecated alias (e.g. "Asia/Calcutta")
		// that PostgreSQL rejects, so pin a zone id Postgres always accepts.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(UrlShortenerServiceApplication.class, args);
	}

}
