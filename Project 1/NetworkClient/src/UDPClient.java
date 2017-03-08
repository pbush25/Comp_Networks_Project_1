/**
 * Created by King on 3/2/2017.
 */
import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.file.Files;

import static java.lang.System.exit;


class UDPClient {
    public static final int PACKET_LENGTH = 128;
    public static DatagramSocket clientSocket;
    public static int damagedPacketProbability;
    public static String fileName;


    public static void main(String args[]) throws Exception {
        // check if a damaged packet probability was passed in
        if (args.length > 0) {
            // set the probability
            try {
                damagedPacketProbability = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Error! Damaged packet probability must be an int!");
                System.out.println("Usage: java UDPClient <int>");
                exit(0);
            }
        }

        clientSocket = new DatagramSocket();
        UDPClientHelper client = new UDPClientHelper("127.0.0.1");

        while(true) {
            BufferedReader inputBuffer = new BufferedReader(new InputStreamReader(System.in));
            try {
                String httpRequest = inputBuffer.readLine();
                String[] requestComponents = (httpRequest.trim().split("\\s+"));
                if (requestComponents.length != 3) {
                    // bad request
                    System.out.println("Bad request! Usage: java UDPClient GET TestFile.html HTTP/2.0");
                    return;
                }
                fileName = requestComponents[1];
                client.sendHTTPRequest(httpRequest);
                client.loadFileFromResponse();
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Client error " + e.getLocalizedMessage());
                exit(1);
            }
        }
    }
}

class UDPClientHelper {
    private byte[] sendData;
    private byte[] receiveData = new byte[UDPClient.PACKET_LENGTH];
    private InetAddress ipAddress;
    private byte[] file;

    public UDPClientHelper(String ipAddressAssigned) {
        try {
            ipAddress = InetAddress.getByName(ipAddressAssigned);
        } catch (UnknownHostException e) {
            System.out.println("Unable to resolve host " + e.getLocalizedMessage());
            exit(1);
        }
    }

    public void sendHTTPRequest(String request) throws IOException {
        sendData = request.getBytes();

        // Create the datagram packet
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, 10040);
        try {
            UDPClient.clientSocket.send(sendPacket);
        } catch (IOException e) {
            System.out.println("Error sending packet... " + e.getLocalizedMessage());
            throw e;
        }
    }

    public void loadFileFromResponse() {
        // get the first packet
        int fileSize = 0;
        String fileInfo;
        try {
            byte[] packet = receivePacket();
            int start = new String(packet).lastIndexOf(':');
            int end = new String(packet).indexOf("\r", start);
            fileSize = Integer.parseInt(new String(packet).substring(start + 1, end).trim());
            fileInfo = new String(packet).substring(81);
        } catch (IOException | NumberFormatException e) {
            return;
        }

        while (fileInfo.length() < fileSize) {
            try {
                byte[] packet = receivePacket();
                fileInfo += new String(packet).substring(81);
                if (packet[packet.length - 2] == 0) {
                    // found the null terminator, write and close file
                    file = fileInfo.getBytes();
                    writeFile();
                }
            } catch (IOException e) {
                return;
            }
        }

    }

    private void writeFile() {
        File localFile = new File("./" + UDPClient.fileName);
        if (!localFile.isFile()) {
            // create file
            try {
                localFile.createNewFile();
            } catch (IOException e) {
                System.out.println("Unable to convert file path! " + e.getLocalizedMessage());
                return;
            }
        }

        try {
            Files.write(localFile.toPath(), file);
            System.out.println("Wrote file successfully!");
        } catch (IOException e) {
            System.out.println("Unable to write to file! Error: " + e.getLocalizedMessage());
            return;
        }

    }

    private byte[] receivePacket() throws IOException {
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        try {
            UDPClient.clientSocket.receive(receivePacket);
        } catch (IOException e) {
            System.out.println("Error receiving packet... " + e.getLocalizedMessage());
            throw e;
        }

        String response = new String(receivePacket.getData());
        System.out.println("Received message from server... " + response);
        return receiveData;
    }

//    private byte gremlin(byte packet) {
//        // First, decide whether to damage packet or not
//
//        if (/** we shouldn't change it */) {
//            return packet;
//        }
//
//        // We decided to damage it
//        // Let's determine how much to damage
//        switch (UDPClient.damagedPacketProbability) {
//            case
//        }
//    }
}
