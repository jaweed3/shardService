package com.example.shardService.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

/**
 * Shard0Config
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.shardService.repository.shard0", entityManagerFactoryRef = "shard0Emf", transactionManagerRef = "shard0Tx")
public class Shard0Config {

  @Bean
  @Primary
  public DataSource shard0DataSource() {
    return DataSourceBuilder.create()
        .url("jdbc:h2:mem:shard0;DB_CLOSE_DELAY=1")
        .username("sa")
        .password("")
        .driverClassName("org.h2.Driver")
        .build();
  }

  @Bean
  @Primary
  public LocalContainerEntityManagerFactoryBean shard0Emf(
      @Qualifier("shard0DataSource") DataSource ds) {
    LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();

    emf.setDataSource(ds);
    emf.setPackagesToScan("com.example.shardService.entity");
    emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

    var props = new java.util.Properties();
    props.setProperty("hibernate.hbm2ddl.auto", "update");
    props.setProperty("hibernate.show_sql", "true");
    props.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
    emf.setJpaProperties(props);

    return emf;
  }

  @Bean
  @Primary
  public PlatformTransactionManager shard0Tx(
      @Qualifier("shard0Emf") EntityManagerFactory emf) {
    return new JpaTransactionManager(emf);
  }
}
