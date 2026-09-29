package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import com.github.kpavlov.jreactive8583.iso.J8583MessageFactory;
import com.nantaaditya.sotres.model.constant.PackagerConstant;
import com.solab.iso8583.IsoMessage;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@DisplayName("MessageFactoryHelper")
class MessageFactoryHelperTest {

  @Nested
  @DisplayName("constructor(PackagerConstant)")
  class Constructor {

    @Test
    @DisplayName("builds a usable default message factory for a valid packager config")
    void constructor_validPackager_buildsDefaultMessageFactory() {
      MessageFactoryHelper helper = new MessageFactoryHelper(PackagerConstant.DEFAULT);

      assertThat(helper.getDefaultMessageFactory())
          .isNotNull()
          .isInstanceOf(J8583MessageFactory.class);
    }

    @Test
    @DisplayName("throws IllegalStateException instead of silently swallowing when packager config fails to load")
    void constructor_packagerLoadFails_throwsIllegalStateException() {
      try (MockedStatic<MessageFactoryHelper.CustomConfigParser> mocked =
          mockStatic(MessageFactoryHelper.CustomConfigParser.class)) {
        mocked.when(() ->
                MessageFactoryHelper.CustomConfigParser.createFromClasspathConfig(PackagerConstant.DEFAULT.getPath()))
            .thenThrow(new IOException("malformed packager config"));

        assertThatThrownBy(() -> new MessageFactoryHelper(PackagerConstant.DEFAULT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("could not initialize packager")
            .hasCauseInstanceOf(IOException.class);
      }
    }
  }

  @Nested
  @DisplayName("createMessageFactory(PackagerConstant)")
  class CreateMessageFactory {

    @Test
    @DisplayName("returns a usable message factory for a valid packager config")
    void createMessageFactory_validPackager_returnsMessageFactory() {
      MessageFactoryHelper helper = new MessageFactoryHelper(PackagerConstant.DEFAULT);

      com.github.kpavlov.jreactive8583.iso.MessageFactory<IsoMessage> factory =
          helper.createMessageFactory(PackagerConstant.DEFAULT);

      assertThat(factory).isNotNull().isInstanceOf(J8583MessageFactory.class);
    }

    @Test
    @DisplayName("wraps a packager load failure in IllegalArgumentException")
    void createMessageFactory_packagerLoadFails_throwsIllegalArgumentException() {
      MessageFactoryHelper helper = new MessageFactoryHelper(PackagerConstant.DEFAULT);

      try (MockedStatic<MessageFactoryHelper.CustomConfigParser> mocked =
          mockStatic(MessageFactoryHelper.CustomConfigParser.class)) {
        mocked.when(() ->
                MessageFactoryHelper.CustomConfigParser.createFromClasspathConfig(PackagerConstant.DEFAULT.getPath()))
            .thenThrow(new IOException("malformed packager config"));

        assertThatThrownBy(() -> helper.createMessageFactory(PackagerConstant.DEFAULT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasCauseInstanceOf(IOException.class);
      }
    }
  }
}
