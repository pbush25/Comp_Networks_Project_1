/**
 * Created by King on 3/2/2017.
 */
import java.io.*;
import java.net.*;

class UDPServer {
    public static DatagramSocket serverSocket;

    public static void main(String args[]) throws Exception {
        try {
            serverSocket = new DatagramSocket(10040);
        } catch (SocketException e) {
            System.out.println("Unable to open socket... " + e.getLocalizedMessage());
            throw e;
        }

        UDPServerHelper server = new UDPServerHelper();
        server.receivePacket();


//        while (true) {
//            //Receive datagram
//
//
//            String sentence = new String(receivePacket.getData());
//
//            InetAddress IPAddress = receivePacket.getAddress();
//
//            int port = receivePacket.getPort();
//
//            String capitalizedSentence = sentence.toUpperCase();
//
//            sendData = capitalizedSentence.getBytes();
//
//
//        }
    }
}

class UDPServerHelper {
    byte[] receiveData = new byte[1024];
    byte[] sendData = new byte[1024];

    public void receivePacket() throws IOException {
        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            try {
                UDPServer.serverSocket.receive(receivePacket);
            } catch (IOException e) {
                System.out.println("Unable to receive packet... " + e.getLocalizedMessage());
                throw e;
            }

            String request = new String(receivePacket.getData());
            if (processRequest(request)) {
                int port = receivePacket.getPort();
                InetAddress ipAddress = receivePacket.getAddress();
                sendPacket(port, ipAddress);
            }

        }
    }

    public void sendPacket(int port, InetAddress ipAddress) throws IOException {
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, port);
        try {
            UDPServer.serverSocket.send(sendPacket);
        } catch (IOException e) {
            System.out.println("Unable to send packet... " + e.getLocalizedMessage());
            throw e;
        }
    }

    public boolean processRequest(String request) {
        return true;
    }
}
