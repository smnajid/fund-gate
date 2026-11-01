package com.fundgate.shareclasscatalog;

import com.fundgate.shareclasses.ShareClasses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** The dealer app reads the catalog once at startup; if it cannot, the app does not start (contracts/share-classes.md). */
@Configuration
@Profile("!pipeline")
public class ShareClassCatalogConfiguration {

    @Bean
    ShareClassCatalog shareClassCatalog(ShareClasses shareClasses) {
        return new ShareClassCatalog(shareClasses);
    }
}
