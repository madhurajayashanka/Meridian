package com.meridian.common.controller;

import com.meridian.common.exception.ValidationException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/**
 * Explicitly maps schema mutations that are not yet implemented.
 *
 * This avoids GraphQL defaulting to null for non-null mutation return types.
 */
@Controller
public class FeatureGateGraphQLController {

    private static ValidationException notImplemented(String feature) {
        return new ValidationException(feature + " is not implemented yet.");
    }

    @MutationMapping
    public Object uploadDocument(@Argument String projectId, @Argument Object file) {
        throw notImplemented("uploadDocument");
    }

    @MutationMapping
    public Boolean deleteDocument(@Argument String id) {
        throw notImplemented("deleteDocument");
    }

    @MutationMapping
    public String exportReport(@Argument String id, @Argument String format) {
        throw notImplemented("exportReport");
    }

    @MutationMapping
    public Boolean deleteReport(@Argument String id) {
        throw notImplemented("deleteReport");
    }

    @MutationMapping
    public Boolean publishReport(@Argument String id) {
        throw notImplemented("publishReport");
    }

    @MutationMapping
    public Boolean unpublishReport(@Argument String id) {
        throw notImplemented("unpublishReport");
    }

}
