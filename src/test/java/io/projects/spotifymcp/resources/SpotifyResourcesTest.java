package io.projects.spotifymcp.resources;

import io.projects.spotifymcp.tools.StateTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpotifyResourcesTest {

    private StateTools stateTools;
    private SpotifyResources resources;

    @BeforeEach
    void setUp() {
        stateTools = mock(StateTools.class);
        resources = new SpotifyResources(stateTools);
    }

    @Test
    void nowPlayingDelegatesToStateTools() {
        when(stateTools.getCurrentlyPlaying()).thenReturn("Playing: 'Song' by Artist");

        assertThat(resources.nowPlaying()).isEqualTo("Playing: 'Song' by Artist");
    }

    @Test
    void devicesDelegatesToStateTools() {
        when(stateTools.getActiveDevices()).thenReturn("MacBook Pro (Computer, active)");

        assertThat(resources.devices()).isEqualTo("MacBook Pro (Computer, active)");
    }
}
