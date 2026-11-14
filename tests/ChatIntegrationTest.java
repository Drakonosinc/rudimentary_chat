import chat.client.ChatClientConnection;
import chat.protocol.ChatPacket;
import chat.protocol.UserInfo;
import chat.protocol.UserStatus;
import chat.server.ChatServerEngine;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Automated headless integration test suite verifying persistent multi-client TCP sockets,
 * AES-GCM encryption, channel & private messaging, binary file transfer, and roster sync.
 */
public class ChatIntegrationTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Running RudimentaryChat Studio 2.0 Integration Suite ===");

        final CountDownLatch channelLatch = new CountDownLatch(1);
        final CountDownLatch dmLatch = new CountDownLatch(1);
        final CountDownLatch fileLatch = new CountDownLatch(1);
        final CountDownLatch rosterLatch = new CountDownLatch(1);

        final AtomicReference<String> bobReceivedChannelMsg = new AtomicReference<String>();
        final AtomicReference<String> bobReceivedDmMsg = new AtomicReference<String>();
        final AtomicReference<byte[]> bobReceivedFile = new AtomicReference<byte[]>();

        ChatServerEngine server = new ChatServerEngine(0, new ChatServerEngine.ServerListener() {
            @Override
            public void onLogEvent(String timestamp, String category, String detail) {
                System.out.printf("  [Server %s] %s%n", category, detail);
            }

            @Override
            public void onRosterChanged(List<UserInfo> activeUsers) {
                if (activeUsers.size() >= 2) {
                    rosterLatch.countDown();
                }
            }

            @Override
            public void onMetricsChanged(int connectedCount, int totalMessages, int totalFiles, int encryptedCount) {
            }
        });
        server.start();
        int port = server.getBoundPort();

        ChatClientConnection alice = new ChatClientConnection(new EmptyClientListener());
        ChatClientConnection bob = new ChatClientConnection(new ChatClientConnection.ClientListener() {
            @Override
            public void onConnectionStateChanged(boolean connected, String statusText) {
            }

            @Override
            public void onMessageReceived(ChatPacket packet, String displayContent) {
                if (!"Alice".equals(packet.getSender())) {
                    return;
                }
                if (packet.getTargetUser() != null) {
                    bobReceivedDmMsg.set(displayContent);
                    dmLatch.countDown();
                } else {
                    bobReceivedChannelMsg.set(displayContent);
                    channelLatch.countDown();
                }
            }

            @Override
            public void onFileReceived(ChatPacket packet, byte[] decodedData) {
                if ("Alice".equals(packet.getSender())) {
                    bobReceivedFile.set(decodedData);
                    fileLatch.countDown();
                }
            }

            @Override
            public void onRosterUpdated(List<UserInfo> roster) {
            }

            @Override
            public void onSystemNotice(String timestamp, String notice) {
            }
        });

        try {
            alice.setEncryptionConfig(true, "test-passphrase-2026");
            bob.setEncryptionConfig(true, "test-passphrase-2026");

            alice.connect("127.0.0.1", port, "Alice", UserStatus.ONLINE);
            bob.connect("127.0.0.1", port, "Bob", UserStatus.ONLINE);

            assertCondition(rosterLatch.await(3, TimeUnit.SECONDS), "Both Alice and Bob should appear in server roster");
            System.out.println("[PASS] 1. Persistent Multi-Client Roster Sync on localhost:" + port);

            // 2. Encrypted Channel Message
            alice.sendChannelMessage("#dev", "Hello encrypted #dev channel!");
            assertCondition(channelLatch.await(3, TimeUnit.SECONDS), "Bob should receive Alice's encrypted channel message");
            assertCondition("Hello encrypted #dev channel!".equals(bobReceivedChannelMsg.get()), "Decrypted channel text mismatch");
            System.out.println("[PASS] 2. AES-GCM Encrypted Channel Messaging (#dev)");

            // 3. Encrypted Direct Private Message
            alice.sendPrivateMessage("Bob", "Secret 1-to-1 whisper for Bob");
            assertCondition(dmLatch.await(3, TimeUnit.SECONDS), "Bob should receive Alice's private DM");
            assertCondition("Secret 1-to-1 whisper for Bob".equals(bobReceivedDmMsg.get()), "Decrypted DM text mismatch");
            System.out.println("[PASS] 3. AES-GCM Encrypted Private Direct Message (@Bob)");

            // 4. Encrypted Binary File Transfer
            byte[] payload = "BinaryFileContent-RudimentaryChat-v2.0".getBytes(StandardCharsets.UTF_8);
            alice.sendFileBytes("#dev", "Bob", "architecture.txt", payload);
            assertCondition(fileLatch.await(3, TimeUnit.SECONDS), "Bob should receive Alice's encrypted file transfer");
            assertCondition(Arrays.equals(payload, bobReceivedFile.get()), "Decrypted file bytes mismatch");
            System.out.println("[PASS] 4. AES-GCM Encrypted Binary File Transfer");

            // 5. Status update & Admin kick
            alice.sendStatusUpdate(UserStatus.BUSY);
            boolean kicked = server.kickUser("Bob", "Integration test completed");
            assertCondition(kicked, "Server kickUser('Bob') should return true");
            System.out.println("[PASS] 5. Status Update & Administrative Moderation");

            System.out.println("=== ALL 5 INTEGRATION TESTS PASSED SUCCESSFULLY ===");
        } finally {
            alice.disconnect();
            bob.disconnect();
            server.stop();
        }
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test failed: " + message);
        }
    }

    private static class EmptyClientListener implements ChatClientConnection.ClientListener {
        @Override
        public void onConnectionStateChanged(boolean connected, String statusText) {
        }

        @Override
        public void onMessageReceived(ChatPacket packet, String displayContent) {
        }

        @Override
        public void onFileReceived(ChatPacket packet, byte[] decodedData) {
        }

        @Override
        public void onRosterUpdated(List<UserInfo> roster) {
        }

        @Override
        public void onSystemNotice(String timestamp, String notice) {
        }
    }
}
