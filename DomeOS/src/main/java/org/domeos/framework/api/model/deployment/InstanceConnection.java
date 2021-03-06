package org.domeos.framework.api.model.deployment;

import io.fabric8.kubernetes.client.dsl.LogWatch;
import org.domeos.exception.JobLogException;
import org.domeos.framework.engine.k8s.NodeWrapper;
import org.domeos.framework.engine.websocket.Connection;
import org.domeos.global.ClientConfigure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.IOException;
import java.io.PipedInputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by feiliu206363 on 2015/12/21.
 */
public class InstanceConnection implements Connection {
    private int clusterId;
    private String namespace;
    private String podName;
    private String containerName;
    private Session session;
    private Logger logger = LoggerFactory.getLogger(InstanceConnection.class);

    private WatchContainerLog watchContainerLog;

    public int getClusterId() {
        return clusterId;
    }

    public void setClusterId(int clusterId) {
        this.clusterId = clusterId;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public void sendMessage() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        watchContainerLog = new WatchContainerLog(clusterId, namespace, podName, containerName);
        ClientConfigure.executorService.submit(new WatchContainerLogTask(watchContainerLog));
    }

    @Override
    public void stopMessage() {
        watchContainerLog.stopRun();
    }


    private class WatchContainerLog {
        private NodeWrapper nodeWrapper;
        private LogWatch logWatch;
        private PipedInputStream pipedInputStream;
        private String podName;
        private String containerName;

        public WatchContainerLog(int clusterId, String namespace, String podName, String containerName) throws Exception {
            this.podName = podName;
            this.containerName = containerName;
            this.nodeWrapper = new NodeWrapper().init(clusterId, namespace);
        }

        private void startRun() {
            try {
                logWatch = nodeWrapper.fetchContainerLogs(podName, containerName);
                pipedInputStream = (PipedInputStream) logWatch.getOutput();
                readMessageContinued();
            } catch (JobLogException e) {
                logger.warn("get exception when get container log, message is " + e.getMessage());
            }
        }

        private void stopRun() {
            try {
                pipedInputStream.close();
                logWatch.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void readMessageContinued() {
            try {
                while (true) {
                    byte[] buf = new byte[1024];
                    int len = pipedInputStream.read(buf);
                    session.getBasicRemote().sendText(new String(buf, 0, len));
                    String pingString = "hello pings";
                    ByteBuffer pingData = ByteBuffer.allocate(pingString.getBytes().length);
                    pingData.put(pingString.getBytes()).flip();
                    session.getBasicRemote().sendPing(pingData);
                    if (len == 0) {
                        stopRun();
                    }
                }
            } catch (IOException e) {
                logger.warn("get exception when send container log, message is " + e.getMessage());
                stopRun();
            }

        }
    }

    private class WatchContainerLogTask implements Callable<WatchContainerLog> {
        private WatchContainerLog watchContainerLog;

        public WatchContainerLogTask(WatchContainerLog watchContainerLog) {
            this.watchContainerLog = watchContainerLog;
        }


        public WatchContainerLog call() throws Exception {
            watchContainerLog.startRun();
            return null;
        }

    }

}
