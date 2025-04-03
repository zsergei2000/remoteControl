package org.example;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class RemoteControlServer {

    private static final int COMMAND_PORT = 12345;
    private static final int IMAGE_PORT = 12346;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        JFrame frame = new JFrame("Экран телефона");
        JLabel label = new JLabel();
        frame.add(label);
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Поток приёма команд
        new Thread(() -> {
            while (true) {
                try (ServerSocket commandServer = new ServerSocket(COMMAND_PORT)) {
                    System.out.println("Ожидание подключения клиента для команд...");
                    Socket commandSocket = commandServer.accept();
                    System.out.println("Клиент подключён к командному порту.");

                    BufferedReader in = new BufferedReader(new InputStreamReader(commandSocket.getInputStream()));
                    String command;
                    while ((command = in.readLine()) != null) {
                        processCommand(command, robot);
                    }
                } catch (IOException e) {
                    System.err.println("Ошибка командного соединения: " + e.getMessage());
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
        }).start();

        // Поток приёма изображений
        while (true) {
            try (ServerSocket imageServer = new ServerSocket(IMAGE_PORT)) {
                System.out.println("Ожидание подключения клиента для изображений...");
                Socket imageSocket = imageServer.accept();
                System.out.println("Клиент подключён к порту изображений.");

                DataInputStream dataInputStream = new DataInputStream(imageSocket.getInputStream());

                while (true) {
                    int length;
                    try {
                        length = dataInputStream.readInt();
                    } catch (EOFException eof) {
                        System.out.println("Клиент отключился.");
                        break;
                    }

                    if (length <= 0) continue;

                    byte[] imageBytes = new byte[length];
                    dataInputStream.readFully(imageBytes);

                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                    if (img != null) {
                        Image scaled = img.getScaledInstance(label.getWidth(), label.getHeight(), Image.SCALE_SMOOTH);
                        label.setIcon(new ImageIcon(scaled));
                        frame.repaint();
                    }
                }
            } catch (IOException e) {
                System.err.println("Ошибка приёма изображения: " + e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private static void processCommand(String command, Robot robot) {
        try {
            if (command.startsWith("MOVE")) {
                String[] parts = command.split(" ");
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                robot.mouseMove(x, y);
            } else if (command.startsWith("CLICK")) {
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            } else if (command.startsWith("TYPE")) {
                String text = command.substring(5);
                for (char c : text.toCharArray()) {
                    int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
                    if (KeyEvent.CHAR_UNDEFINED == keyCode) continue;
                    robot.keyPress(keyCode);
                    robot.keyRelease(keyCode);
                }
            } else if (command.startsWith("PASTE")) {
                String text = command.substring(6);
                StringSelection selection = new StringSelection(text);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, null);
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_V);
                robot.keyRelease(KeyEvent.VK_V);
                robot.keyRelease(KeyEvent.VK_CONTROL);
            }
        } catch (Exception e) {
            System.err.println("Ошибка обработки команды: " + command);
            e.printStackTrace();
        }
    }
}
