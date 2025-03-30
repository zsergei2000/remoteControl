package org.example;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.awt.event.InputEvent;
import java.awt.datatransfer.*;
import java.awt.Toolkit;

public class RemoteControlServer {
    private static final int COMMAND_PORT = 12345;  // Порт для команд
    private static final int IMAGE_PORT = 12346;    // Порт для изображений
    private static int lastX = -1, lastY = -1;      // Последние координаты мыши
    public static void main(String[] args) {
        try {
            // Запускаем 2 потока для обработки разных подключений
            new Thread(() -> startCommandServer()).start();
            new Thread(() -> startImageServer()).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Сервер для обработки команд управления
    private static void startCommandServer() {
        try (ServerSocket serverSocket = new ServerSocket(COMMAND_PORT)) {
            System.out.println("Command Server started on port " + COMMAND_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected to command server: " + clientSocket.getInetAddress());

                new Thread(() -> handleCommands(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Сервер для отправки изображений экрана клиенту
    private static void startImageServer() {
        try (ServerSocket serverSocket = new ServerSocket(IMAGE_PORT)) {
            System.out.println("Image Server started on port " + IMAGE_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected to image server: " + clientSocket.getInetAddress());

                new Thread(() -> sendImages(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Обработчик команд клавиатуры и мыши

    private static void handleCommands(Socket socket) {
        try (DataInputStream dataIn = new DataInputStream(socket.getInputStream());
             DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream())) {
            Robot robot = new Robot();

            while (true) {
                int length = dataIn.readInt();
                byte[] buffer = new byte[length];
                dataIn.readFully(buffer);
                String command = new String(buffer, "UTF-8");

                // Logging the received command
                System.out.println("Received command: " + command);

                if (command.startsWith("MOUSE_PRESS:")) {
                    int button = Integer.parseInt(command.split(":")[1]);
                    robot.mousePress(InputEvent.getMaskForButton(button));
                } else if (command.startsWith("MOUSE_RELEASE:")) {
                    int button = Integer.parseInt(command.split(":")[1]);
                    robot.mouseRelease(InputEvent.getMaskForButton(button));
                } else if (command.contains(",")) { // Handle mouse movement
                    String[] coords = command.split(",");
                    int x = Integer.parseInt(coords[0]);
                    int y = Integer.parseInt(coords[1]);

                    if (x != lastX || y != lastY) {
                        robot.mouseMove(x, y);
                        lastX = x;
                        lastY = y;
                    }
                } else if (command.equals("COPY")) {
                    // Copy selected text to clipboard and send it back to the client
                    String copiedText = copySelectedTextToClipboard(robot);
                    sendCopiedText(dataOut, copiedText);
                } else if (command.equals("PASTE")) {
                    // Paste text from clipboard
                    pasteTextFromClipboard(robot);
                } else if (command.equals("CUT")) {
                    // Cut text to clipboard
                    cutTextToClipboard(robot);
                } else if (command.equals("UNDO")) {
                    // Undo action
                    undoAction(robot);
                } else if (command.equals("DELETE")) {
                    // Delete text
                    deleteText(robot);
                } else { // Handle key press
                    pressKey(command, robot);
                }
            }
        } catch (IOException | AWTException e) {
            System.out.println("Client disconnected from command server.");
        }
    }

    private static String copySelectedTextToClipboard(Robot robot) {
        try {
            // Очищаем буфер обмена перед копированием
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(""), null);
            System.out.println("[LOG] Clipboard очищен перед копированием.");

            // Эмулируем нажатие Ctrl+C
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_C);
            robot.keyRelease(KeyEvent.VK_C);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            System.out.println("[LOG] Эмулировано нажатие Ctrl+C.");

            // Дадим системе время обновить буфер обмена
            Thread.sleep(200); // Можно увеличить при необходимости

            // Получаем содержимое буфера обмена
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String copiedText = (String) contents.getTransferData(DataFlavor.stringFlavor);
                System.out.println("[LOG] Содержимое буфера обмена: \"" + copiedText + "\"");
                return copiedText;
            } else {
                System.out.println("[LOG] Буфер обмена не содержит текст.");
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Ошибка при копировании текста в буфер обмена:");
            e.printStackTrace();
        }
        return null;
    }


    // Method to send copied text back to the client
    private static void sendCopiedText(DataOutputStream dataOut, String text) {
        try {
            byte[] buffer = text.getBytes("UTF-8");
            dataOut.writeInt(buffer.length);
            dataOut.write(buffer);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    private static void cutTextToClipboard(Robot robot) {
        try {
            // Используем сочетание клавиш Ctrl+X для вырезания
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_X);
            robot.keyRelease(KeyEvent.VK_X);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            // Логируем действие вырезания
            System.out.println("Cut text to clipboard.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void undoAction(Robot robot) {
        try {
            // Используем сочетание клавиш Ctrl+Z для отмены
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_Z);
            robot.keyRelease(KeyEvent.VK_Z);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            // Логируем действие отмены
            System.out.println("Undo action performed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void deleteText(Robot robot) {
        try {
            // Используем сочетание клавиш Ctrl+D для удаления
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_D);
            robot.keyRelease(KeyEvent.VK_D);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            // Логируем действие удаления
            System.out.println("Deleted text.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private static void pasteTextFromClipboard(Robot robot) {
        try {
            // Используем сочетание клавиш Ctrl+V для вставки
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            // Получаем текст из буфера обмена
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);

            // Проверяем, поддерживается ли текстовый формат
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                System.out.println("Pasted text: " + text);
            } else {
                System.out.println("Clipboard does not contain text or contains unsupported data.");
            }
        } catch (UnsupportedFlavorException | IOException e) {
            // Игнорируем ошибки, связанные с неподдерживаемыми данными в буфере обмена
            System.out.println("Error accessing clipboard data: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    // Отправка изображения клиенту
    private static void sendImages(Socket socket) {
        try (DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream())) {
            Robot robot = new Robot();

            while (true) {
                BufferedImage screenshot = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ImageIO.write(screenshot, "PNG", byteArrayOutputStream);
                byte[] imageBytes = byteArrayOutputStream.toByteArray();

                // Логируем размер изображения
                System.out.println("Sending screenshot (" + imageBytes.length + " bytes)");

                // Отправляем данные
                dataOut.writeInt(imageBytes.length);
                dataOut.write(imageBytes);
                dataOut.flush();

                Thread.sleep(100); // Интервал обновления изображения
            }
        } catch (IOException | InterruptedException | AWTException e) {
            System.out.println("Client disconnected from image server.");
        }
    }

    // Метод для обработки нажатий клавиш
    private static void pressKey(String key, Robot robot) {
        try {
            int keyCode = getKeyCode(key);
            if (keyCode != -1) {
                if (key.startsWith("CTRL+")) {
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.keyPress(keyCode);
                    Thread.sleep(100);
                    robot.keyRelease(keyCode);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                } else {
                    robot.keyPress(keyCode);
                    Thread.sleep(100);
                    robot.keyRelease(keyCode);
                }
                System.out.println("Simulated key press: " + key); // Логирование обработанной команды
            } else {
                System.out.println("Unknown key: " + key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Метод для сопоставления текста с кодами клавиш
    private static int getKeyCode(String key) {
        switch (key.toUpperCase()) {
            case "ENTER": return KeyEvent.VK_ENTER;
            case "ESC": return KeyEvent.VK_ESCAPE;
            case "SHIFT": return KeyEvent.VK_SHIFT;
            case "CONTROL": return KeyEvent.VK_CONTROL;
            case "ALT": return KeyEvent.VK_ALT;
            case "TAB": return KeyEvent.VK_TAB;
            case "SPACE": return KeyEvent.VK_SPACE;
            case "BACKSPACE": return KeyEvent.VK_BACK_SPACE;
            case "LEFT": return KeyEvent.VK_LEFT;
            case "UP": return KeyEvent.VK_UP;
            case "RIGHT": return KeyEvent.VK_RIGHT;
            case "DOWN": return KeyEvent.VK_DOWN;
            case "A": return KeyEvent.VK_A;
            case "B": return KeyEvent.VK_B;
            case "C": return KeyEvent.VK_C;
            case "D": return KeyEvent.VK_D;
            case "E": return KeyEvent.VK_E;
            case "F": return KeyEvent.VK_F;
            case "G": return KeyEvent.VK_G;
            case "H": return KeyEvent.VK_H;
            case "I": return KeyEvent.VK_I;
            case "J": return KeyEvent.VK_J;
            case "K": return KeyEvent.VK_K;
            case "L": return KeyEvent.VK_L;
            case "M": return KeyEvent.VK_M;
            case "N": return KeyEvent.VK_N;
            case "O": return KeyEvent.VK_O;
            case "P": return KeyEvent.VK_P;
            case "Q": return KeyEvent.VK_Q;
            case "R": return KeyEvent.VK_R;
            case "S": return KeyEvent.VK_S;
            case "T": return KeyEvent.VK_T;
            case "U": return KeyEvent.VK_U;
            case "V": return KeyEvent.VK_V;
            case "W": return KeyEvent.VK_W;
            case "X": return KeyEvent.VK_X;
            case "Y": return KeyEvent.VK_Y;
            case "Z": return KeyEvent.VK_Z;
            case "1": return KeyEvent.VK_1;
            case "2": return KeyEvent.VK_2;
            case "3": return KeyEvent.VK_3;
            case "4": return KeyEvent.VK_4;
            case "5": return KeyEvent.VK_5;
            case "6": return KeyEvent.VK_6;
            case "7": return KeyEvent.VK_7;
            case "8": return KeyEvent.VK_8;
            case "9": return KeyEvent.VK_9;
            case "0": return KeyEvent.VK_0;
            default: return -1;
        }
    }
}