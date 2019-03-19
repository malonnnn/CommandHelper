<!
    strict;
    name: Regex;
    author: cailin;
    created: 2019-03-13;
    description: The file containing the type ms.lang.manifest.OrganizationInfo;
>

use ms.lang.validators;

/**
 * Provides information about an organization.
 */
public interface ms.lang.manifest.OrganizationInfo {
    /**
     * This is the human readable organization name.
     */
    string @organizationName;

    /**
     * This is the URL of the organization.
     */
    @{ValidUrl}
    string @url;

    /**
     * This is a description of the organization.
     */
    string @description;

    string @comment;
}