package com.meridian.config;

import com.meridian.common.exception.ForbiddenException;
import com.meridian.common.exception.InvalidRequestException;
import com.meridian.common.exception.ResourceNotFoundException;
import com.meridian.common.exception.UnauthorizedException;
import com.meridian.common.exception.ValidationException;
import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

/**
 * Maps domain exceptions to structured GraphQL errors so callers always receive
 * a well-formed {@code errors} array rather than an unresolved server exception.
 */
@Slf4j
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ValidationException || ex instanceof InvalidRequestException) {
            return GraphqlErrorBuilder.newError(env)
                    .message(ex.getMessage())
                    .errorType(ErrorType.ValidationError)
                    .build();
        }

        if (ex instanceof UnauthorizedException) {
            return GraphqlErrorBuilder.newError(env)
                    .message(ex.getMessage())
                    .errorType(ErrorType.ExecutionAborted)
                    .extensions(java.util.Map.of("code", "UNAUTHENTICATED"))
                    .build();
        }

        if (ex instanceof ForbiddenException) {
            return GraphqlErrorBuilder.newError(env)
                    .message(ex.getMessage())
                    .errorType(ErrorType.ExecutionAborted)
                    .extensions(java.util.Map.of("code", "FORBIDDEN"))
                    .build();
        }

        if (ex instanceof ResourceNotFoundException) {
            return GraphqlErrorBuilder.newError(env)
                    .message(ex.getMessage())
                    .errorType(ErrorType.DataFetchingException)
                    .extensions(java.util.Map.of("code", "NOT_FOUND"))
                    .build();
        }

        // Let Spring GraphQL apply default handling for everything else
        log.error("Unhandled GraphQL resolver exception at {}: {}", env.getField().getName(), ex.getMessage(), ex);
        return null;
    }
}
