package com.ahead.aiagent.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * 数据源配置
 * MySQL 作为主数据源（MyBatis）
 * PostgreSQL 作为向量存储数据源（PGVector）
 */
@Configuration
@MapperScan(basePackages = "com.ahead.aiagent.mapper", sqlSessionFactoryRef = "sqlSessionFactory")
public class DataSourceConfig {

    /**
     * MySQL 主数据源（MyBatis 使用）
     */
    @Primary
    @Bean(name = "dataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * MyBatis-Plus SqlSessionFactory
     */
    @Primary
    @Bean(name = "sqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(
            @Qualifier("dataSource") DataSource dataSource,
            MybatisPlusConfig mybatisPlusConfig,
            MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);

        // 配置 MyBatis
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        // 修复 E8：手工构建 SqlSessionFactory 会让 MybatisPlusAutoConfiguration 让位，
        // 导致 yml 中 mybatis-plus.configuration.log-impl 失效，这里显式补回 SQL 日志实现。
        configuration.setLogImpl(StdOutImpl.class);
        bean.setConfiguration(configuration);

        // 修复 E2/E8：挂载 MyBatis-Plus 插件（分页拦截器），否则 selectPage 不分页
        bean.setPlugins(mybatisPlusInterceptor);

        // 设置 MetaObjectHandler
        bean.setGlobalConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig()
                .setMetaObjectHandler(mybatisPlusConfig));

        return bean.getObject();
    }

    /**
     * 事务管理器
     */
    @Primary
    @Bean(name = "transactionManager")
    public DataSourceTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
