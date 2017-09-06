package org.openshift.ping.kube.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openshift.ping.kube.test.FreePortFinder.findFreePort;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jgroups.JChannel;
import org.jgroups.protocols.TCP;
import org.jgroups.protocols.openshift.KUBE_PING;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.junit.Test;
import org.openshift.ping.kube.Client;
import org.openshift.ping.kube.Pod;

public class RollingUpdateTest {

   @Test
   public void testPuttingAllNodesInTheSameClusterDuringRollingUpdate() throws Exception {
      //given
      KUBE_PING_FOR_TESTING testedProtocol = new KUBE_PING_FOR_TESTING("/openshift_rolling_update.json", "myproject");

      //when
      sendInitialDiscovery(testedProtocol);
      List<InetSocketAddress> membersUsedForDiscovery = testedProtocol.getCollectedDiscoveryRequests();
      List<Pod> allPodsFromKubernetesApi = testedProtocol.getAllPodsFromTheClient();

      //then
      assertEquals(membersUsedForDiscovery.size(), allPodsFromKubernetesApi.size());
   }

   @Test
   public void testPutOnlyNodesWithTheSameParentDuringRollingUpdate() throws Exception {
      //given
      KUBE_PING_FOR_TESTING testedProtocol = new KUBE_PING_FOR_TESTING("/openshift_rolling_update.json", "myproject");
      testedProtocol.setValue("split_clusters_during_rolling_update", true);

      //when
      sendInitialDiscovery(testedProtocol);

      String senderParentDeployment = "unset";
      for (Pod p : testedProtocol.getAllPodsFromTheClient()) {
         if ("127.0.0.1".equals(p.getPodIP())) {
            senderParentDeployment = p.getParentDeployment();
            break;
         }
      }

      List<InetSocketAddress> membersUsedForDiscovery = testedProtocol.getCollectedDiscoveryRequests();
      List<Pod> allowedPodsFromKubernetesApi = new ArrayList<>();
      for (Pod p : testedProtocol.getAllPodsFromTheClient()) {
         if (senderParentDeployment.equals(p.getParentDeployment())) {
            allowedPodsFromKubernetesApi.add(p);
         }
      }
      List<Pod> allPodsFromKubernetesApi = testedProtocol.getAllPodsFromTheClient();

      //then
      assertEquals(allowedPodsFromKubernetesApi.size(), membersUsedForDiscovery.size());
      assertTrue(allPodsFromKubernetesApi.size() > membersUsedForDiscovery.size());
   }

   private void sendInitialDiscovery(KUBE_PING kubePingProtocol) throws Exception {
      JChannel jChannel = new JChannel(
            new TCP().setValue("bind_addr", InetAddress.getLoopbackAddress()).setValue("bind_port", findFreePort()),
            kubePingProtocol,
            new NAKACK2(),
            //The join timeout is not strictly necessary but it speeds up the testsuite in JGroups 4
            new GMS().setValue("join_timeout", 1)
      );
      jChannel.connect("RollingUpdateTest");
      jChannel.disconnect();
   }

   static class KUBE_PING_FOR_TESTING extends KUBE_PING {

      private final String resourceFile;
      private List<InetSocketAddress> collectedDiscoveryRequests = new ArrayList<>();
      private List<Pod> pods;

      public KUBE_PING_FOR_TESTING(String resourceFile, String namespace) {
         this.resourceFile = resourceFile;
         super.setNamespace(namespace);
         //Even though it's not necessary, it speeds up the testsuite.
         super.setTimeout(1);
      }

      @Override
      public void init() throws Exception {
         super.init();
      }

      @Override
      protected Client getClient() {
         TestClient client = new TestClient(resourceFile);
         try {
            pods = client.getPods(getNamespace(), getLabels());
         } catch (Exception e) {
            throw new AssertionError("Unexpected", e);
         }
         return client;
      }

      @Override
      protected synchronized List<InetSocketAddress> doReadAll(String clusterName) {
         collectedDiscoveryRequests = super.doReadAll(clusterName);
         //We don't want to send any messages for real...
         return Collections.emptyList();
      }

      public List<InetSocketAddress> getCollectedDiscoveryRequests() {
         return collectedDiscoveryRequests;
      }

      public List<Pod> getAllPodsFromTheClient() {
         return pods;
      }
   }

}
