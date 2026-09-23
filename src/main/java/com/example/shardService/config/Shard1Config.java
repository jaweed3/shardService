package com.example.shardService.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

/**
 * Shard1Config
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.shardService.repository.shard1", entityManagerFactoryRef = "shard1Emf", transactionManagerRef = "shard1Tx")
public class Shard1Config {

  @Bean
  public DataSource shard1DataSource() {
    return DataSourceBuilder.create()
        .url("jdbc:h2:mem:shard1;DB_CLOSE_DELAY=1")
        .username("sa")
        .password("")
        .driverClassName("org.h2.Driver")
        .build();
  }

  @Bean
  public LocalContainerEntityManagerFactoryBean shard1Emf(
      @Qualifier("shard1DataSource") DataSource ds) {
    LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();

    emf.setDataSource(ds);
    emf.setPackagesToScan("com.example.shardService.entity");
    emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

    var props = new java.util.Properties();
    props.setProperty("hibernate.hbm2ddl.auto", "update");
    props.setProperty("hibernate.show_sql", "true");
    emf.setJpaProperties(props);

    return emf;
  }

  @Bean
  public PlatformTransactionManager shard1Tx(
      @Qualifier("shard1Emf") EntityManagerFactory emf) {
    return new JpaTransactionManager(emf);
  }
}
