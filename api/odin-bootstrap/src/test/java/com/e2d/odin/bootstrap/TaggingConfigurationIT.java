package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.port.ProjectProposer;
import com.e2d.odin.application.port.TopicTagger;
import com.e2d.odin.application.topic.TagPendingDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class TaggingConfigurationIT extends AbstractIT {

    @Autowired ApplicationContext context;

    @Test
    void noUrlMeansNoTaggingBeans() {
        assertThat(context.getBeansOfType(TopicTagger.class)).isEmpty();
        assertThat(context.getBeansOfType(ProjectProposer.class)).isEmpty();
        assertThat(context.getBeansOfType(TagPendingDocuments.class)).isEmpty();
        assertThat(context.getBeansOfType(TaggingScheduler.class)).isEmpty();
    }
}
