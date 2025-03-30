package org.example.clien;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class RemoteControlClient {
    private static final int COMMAND_PORT = 12345;  // Port for commands
    private static final int IMAGE_PORT = 12346;    // Port for image transfer
    private static final String SERVER_IP = "192.168.1.248";

    private static boolean[] keyPressedState = new boolean[256];
    private static boolean capsLockOn = false;
    private static boolean leftShiftPressed = false;
    private static String lastKeyCommand = "";

    public static void main(String[] args) {
        try {
            // Connect to the server
            Socket commandSocket = new Socket(SERVER_IP, COMMAND_PORT);
            Socket imageSocket = new Socket(SERVER_IP, IMAGE_PORT);

            DataOutputStream dataOut = new DataOutputStream(commandSocket.getOutputStream());
            DataInputStream dataIn = new DataInputStream(commandSocket.getInputStream());
            DataInputStream imageIn = new DataInputStream(imageSocket.getInputStream());

            System.out.println("Connected to server.");

            // Window for displaying the screen
            JFrame frame = new JFrame("Remote Control");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            frame.setVisible(true);

            // Keyboard listener
            frame.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    String keyCommand = KeyEvent.getKeyText(e.getKeyCode());

                    // Get modifiers (e.g., Ctrl, Shift)
                    int modifiers = e.getModifiersEx();
                    boolean flagInt = true;
                    boolean flagCntrlV = true;

                    // Check if the key press signal has already been sent
                    if (!keyPressedState[e.getKeyCode()]) {
                        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
                            if (keyCommand.equals("C")) {
                                keyCommand = "CTRL+C";
                                flagInt = false;
                            } else if (keyCommand.equals("V")) {
                                keyCommand = "CTRL+V";
                                flagInt = false;
                            } else if (keyCommand.equals("X")) {
                                keyCommand = "CTRL+X";
                                flagInt = false;
                            } else if (keyCommand.equals("A")) {
                                keyCommand = "CTRL+A";
                                flagInt = false;
                            } else if (keyCommand.equals("Z")) {
                                keyCommand = "CTRL+Z";
                                flagInt = false;
                            }
                        }

                        // Check Caps Lock state
                        if (capsLockOn && keyCommand.length() == 1) {
                            keyCommand = keyCommand.toUpperCase();
                        }

                        // Check Shift state
                        if (leftShiftPressed && keyCommand.length() == 1) {
                            keyCommand = keyCommand.toUpperCase();
                        }
                        if (flagInt) {
                            sendCommand(dataOut, keyCommand);
                            keyPressedState[e.getKeyCode()] = true;
                        }
                    }

                    // Handle Caps Lock press
                    if (e.getKeyCode() == KeyEvent.VK_CAPS_LOCK) {
                        capsLockOn = !capsLockOn;
                        System.out.println("Caps Lock is now " + (capsLockOn ? "ON" : "OFF"));
                    }

                    // Handle Shift press
                    if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                        leftShiftPressed = true;
                    }
// Handle copy command
                    if (e.getKeyCode() == KeyEvent.VK_C && (modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
                        sendCommand(dataOut, "COPY");
                        // Wait for the server to send back the copied text
                        String copiedText = receiveCopiedText(dataIn);
                        //--setClipboardText(copiedText);

                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(new StringSelection(""), null); // Очистить

                        Robot robot = null;
                        try {

                            Thread.sleep(50);
                            clipboard.setContents(new StringSelection(copiedText), null); // Установить заново
                            robot = new Robot();
                        } catch (AWTException ex) {
                            throw new RuntimeException(ex);
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }


//                        robot.keyPress(KeyEvent.VK_CONTROL);
//                        robot.keyPress(KeyEvent.VK_V);
//                        robot.keyRelease(KeyEvent.VK_V);
//                        robot.keyRelease(KeyEvent.VK_CONTROL);
                        //flagCntrlV = false;

                        robot.keyPress(KeyEvent.VK_CONTROL);
                        robot.keyPress(KeyEvent.VK_V);
                        robot.keyRelease(KeyEvent.VK_V);
                        robot.keyRelease(KeyEvent.VK_CONTROL);
                        //flagCntrlV = true;
                    }
                    if (flagCntrlV) {
                        // Handle paste command
                        if (e.getKeyCode() == KeyEvent.VK_V && (modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
                            sendCommand(dataOut, "PASTE");
                        }
                    }
                    // Handle cut command
                    if (e.getKeyCode() == KeyEvent.VK_X && (modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
                        sendCommand(dataOut, "CUT");
                        return;
                    }

                    // Handle undo command
                    if (e.getKeyCode() == KeyEvent.VK_Z && (modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
                        if (!lastKeyCommand.equals("UNDO")) {
                            sendCommand(dataOut, "UNDO");
                            lastKeyCommand = "UNDO";
                        }
                        return;
                    }

                    // Handle duplicate command
                    if (e.getKeyCode() == KeyEvent.VK_D && (modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
                        sendCommand(dataOut, "DUPLICATE");
                        lastKeyCommand = "DUPLICATE";
                        return;
                    }

                    // Reset "UNDO" flag if not CTRL+Z
                    if (!(e.getKeyCode() == KeyEvent.VK_Z && (modifiers & InputEvent.CTRL_DOWN_MASK) != 0)) {
                        lastKeyCommand = "OTHER";
                    }

                    // Save the last command if not set above
                    lastKeyCommand = keyCommand;
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    keyPressedState[e.getKeyCode()] = false;

                    // Handle Shift release
                    if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                        leftShiftPressed = false;
                    }
                }
            });

            // Mouse listener
            frame.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    sendCommand(dataOut, "MOUSE_PRESS:" + e.getButton());
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    sendCommand(dataOut, "MOUSE_RELEASE:" + e.getButton());
                }
            });

            // Mouse motion listener
            frame.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    sendCommand(dataOut, e.getXOnScreen() + "," + e.getYOnScreen());
                }
            });

            // Thread for receiving images
            new Thread(() -> {
                while (true) {
                    try {
                        int imageLength = imageIn.readInt();
                        byte[] imageBytes = new byte[imageLength];
                        imageIn.readFully(imageBytes);

                        ImageIcon icon = new ImageIcon(imageBytes);
                        if (icon.getIconWidth() == -1 || icon.getIconHeight() == -1) {
                            System.out.println("Failed to create ImageIcon.");
                            continue;
                        }

                        JLabel label = new JLabel(icon);
                        frame.getContentPane().removeAll();
                        frame.getContentPane().add(label);
                        frame.revalidate();
                        frame.repaint();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendCommand(DataOutputStream out, String command) {
        try {
            byte[] commandBytes = command.getBytes("UTF-8");
            out.writeInt(commandBytes.length);
            out.write(commandBytes);
            System.out.println("Sent: " + command);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to receive copied text from the server
    private static String receiveCopiedText(DataInputStream dataIn) {
        try {
            int length = dataIn.readInt();
            byte[] buffer = new byte[length];
            dataIn.readFully(buffer);
            return new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    // Method to set clipboard text on the client
    private static void setClipboardText(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection selection = new StringSelection(text);
        clipboard.setContents(selection, null);
    }
}
