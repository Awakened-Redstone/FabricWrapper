package org.jigsawlabs.fabricwrapper.jumploader.launch;

import org.jigsawlabs.fabricwrapper.jumploader.launch.serviceloading.FileSystemProviderAppender;
import org.jigsawlabs.fabricwrapper.jumploader.launch.serviceloading.JimfsURLHandlerAppender;

import java.util.Arrays;
import java.util.List;

public class PreLaunchDispatcher {
    public interface Handler {
        void handlePreLaunch(ClassLoader loadingClassloader);
    }

    private static final List<Handler> HANDLERS = Arrays.asList(
            new FileSystemProviderAppender(),
            new JimfsURLHandlerAppender()
    );

    public static void dispatch(ClassLoader loadingClassloader) {
        for (Handler handler : HANDLERS) {
            handler.handlePreLaunch(loadingClassloader);
        }
    }
}
