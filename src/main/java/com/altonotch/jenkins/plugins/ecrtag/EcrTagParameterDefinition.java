package com.altonotch.jenkins.plugins.ecrtag;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ecr.model.DescribeImagesResponse;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Logger;


public class EcrTagParameterDefinition extends ParameterDefinition {
    private final List<String> repositoryArns;
    private final ListBoxModel imageTags;

    private transient EcrClient ecrClient;

    private static final Logger logger = Logger.getLogger(EcrTagParameterDefinition.class.getName());

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public EcrTagParameterDefinition(String name, List<String> repositoryArns) {
        super(name);

        this.repositoryArns = repositoryArns;
        this.ecrClient = EcrClient.builder()
                .credentialsProvider(InstanceProfileCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();

        this.imageTags = getImageTags();
    }

    @Symbol("ecrTag")
    @Extension
    public static class DescriptorImpl extends ParameterDefinition.ParameterDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "ECR Image Tags Parameter";
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        return null;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        return null;
    }

    /**
     * Retrieves the latest image tags for the specified repository ARNs.
     *
     * @return A ListBoxModel containing the combined repository ARN and latest image tag as options.
     */
    private ListBoxModel getImageTags() {
        ListBoxModel imageTags = new ListBoxModel();

        for (String repositoryArn : this.repositoryArns) {
            DescribeImagesRequest request = DescribeImagesRequest
                    .builder()
                    .repositoryName(repositoryArn)
                    .maxResults(1)
                    .build();

            DescribeImagesResponse response;

            try {
                response = ecrClient.describeImages(request);
            } catch (AwsServiceException e) {
                logger.severe("Failed to get image tags for " + repositoryArn);
                throw new RuntimeException(e);
            } catch (SdkClientException e) {
                throw new RuntimeException(e);
            }

            String latestTag = response.imageDetails().stream()
                    .flatMap(imageDetail -> imageDetail.imageTags().stream())
                    .findFirst()
                    .orElse("No Image Tag");

            if ("No Image Tag".equals(latestTag)) {
                logger.warning("Repository " + repositoryArn + " is empty, no image tags found.");
                continue;
            }

            String combinedRepoTag = repositoryArn + ":" + latestTag;

            imageTags.add(combinedRepoTag);
        }

        return imageTags;
    }
}
