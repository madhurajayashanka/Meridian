package com.meridian.config;

import graphql.scalars.ExtendedScalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
public class GraphQlScalarConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        GraphQLScalarType uploadScalar = GraphQLScalarType.newScalar()
            .name("Upload")
            .description("Represents a file upload payload")
            .coercing(new Coercing<Object, Object>() {
                @Override
                public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                    return dataFetcherResult;
                }

                @Override
                public Object parseValue(Object input) throws CoercingParseValueException {
                    return input;
                }

                @Override
                public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                    return input;
                }
            })
            .build();

        return wiringBuilder -> wiringBuilder
            .scalar(ExtendedScalars.GraphQLLong)
            .scalar(uploadScalar);
    }
}
