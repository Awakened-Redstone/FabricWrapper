package org.jigsawlabs.fabricwrapper;

public class ShutdownHook implements Runnable {

    @Override
    public void run() {
        System.err.println("The shutdown hook has been called, JVM stopping? Should it be? Is there any exceptions?");
    }
}
