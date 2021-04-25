import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;


public class JavaRace extends Application implements EventHandler<ActionEvent> {

    Image track = new Image("/track.png");
    Image bg = new Image("/bg.png");
    Image finish = new Image("/finish.png");


    //gui
    private static final double W = 1280, H = 780;
    Button btnConnect = new Button("Connect");
    Button btnHost = new Button("Host");
    Button btnQuit = new Button("Quit");
    Button btnStart = new Button("Start");
    public Stage stage;
    StackPane rootGame;
    ListView<String> chatList;
    ObservableList<String> chatLog = FXCollections.observableArrayList();
    Scene mainMenuScene, gameScene, chatScene;
    PrintWriter pwt = null;
    Scanner scn = null;
    int index = 0, currentIndex = 0;
    ArrayList<ClientThread> clients = new ArrayList<>();
    ArrayList<StackPane> cars = new ArrayList<>();
    Group map;
    boolean running, isDriving = false, isStopping = false, isReversing = false, isTurning = false, left = false;
    public float mAcceleration = 0;
    public int MAX_SPEED;
    public int MAX_LAPS;
    public int SERVER_PORT;
    public int MAX_PLAYERS;
    boolean isIdle = true;
    double rotation, rotateBy;
    ArrayList<Image> carImages = new ArrayList<>();
    BufferedImage backg;
    javafx.scene.shape.Rectangle finishRect;
    Label lblLap;
    boolean newLapTimedOut = true;
    int numLaps = 0;
    Label lblWinner;
    HBox uiPane;
    boolean host = false;
    private ArrayList<Shape> puddles = new ArrayList<Shape>();
    private ArrayList<Point2D> puddlesLocations = new ArrayList<Point2D>();
    private javafx.scene.shape.Rectangle puddleRect;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            public void handle(WindowEvent we) {
                System.exit(1);
            }
        });
        stage.setTitle("Game");
        XMLSettings gameWorker = new XMLSettings("settings.xml");
        gameWorker.readXML();
        //main menu
        VBox vbox = new VBox();
        vbox.setAlignment(Pos.CENTER);
        vbox.getChildren().addAll(btnConnect, btnHost, btnQuit);
        mainMenuScene = new Scene(vbox, 400, 400);
        btnConnect.setOnAction(this);
        btnHost.setOnAction(this);
        btnQuit.setOnAction(this);

        //chat
        VBox chatBox = new VBox();
        chatList = new ListView<String>();
        chatList.setItems(chatLog);
        btnStart.setOnAction(this);
        TextField tfChat = new TextField();
        tfChat.setPromptText("Write message...");
        tfChat.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                sendMessage(tfChat.getText());
                chatLog.add(tfChat.getText());
                chatList.setItems(chatLog);
                tfChat.clear();
            }
        });
        chatBox.getChildren().addAll(chatList, tfChat, btnStart);
        chatScene = new Scene(chatBox, 200, 400);

        //map
        ImageView bgImage = new ImageView(bg);
        ImageView trackImage = new ImageView(track);

        uiPane = new HBox();
        uiPane.setPrefWidth(W);
        uiPane.setPrefHeight(H);
        uiPane.setAlignment(Pos.TOP_LEFT);

        rootGame = new StackPane();
        lblLap = new Label("Lap " + numLaps + "/3");
        lblLap.setStyle("-fx-text-fill: white;  -fx-font-size: 20pt;");
        lblLap.setPadding(new Insets(20));
        uiPane.getChildren().add(lblLap);
        lblLap.setAlignment(Pos.BOTTOM_CENTER);

        lblWinner = new Label();

        map = new Group();
        trackImage.setPreserveRatio(true);
        bgImage.setPreserveRatio(true);

        map.getChildren().addAll(bgImage);
        map.getChildren().addAll(trackImage);

        carImages.add(new Image("/car red.png"));
        carImages.add(new Image("/car green.png"));
        carImages.add(new Image("/car blue.png"));
        carImages.add(new Image("/car yellow.png"));

        backg = ImageIO.read(getClass().getResource("/track.png"));

        ImageView finishView = new ImageView(finish);
        finishRect = new javafx.scene.shape.Rectangle(finish.getWidth(), finish.getHeight());
        StackPane finishStack = new StackPane(finishRect, finishView);
        finishStack.relocate(217, 377);
        map.getChildren().add(finishStack);
        rootGame.getChildren().addAll(map, uiPane);

        puddlesLocations.add(new Point2D(340, 250));
        puddlesLocations.add(new Point2D(638, 235));
        puddlesLocations.add(new Point2D(638, 540));

        Image puddle = new Image("/puddle.png");

        for (int i = 0; i < 3; i++) {
            ImageView puddleImg = new ImageView(puddle);
            puddleRect = new javafx.scene.shape.Rectangle(puddle.getWidth(), puddle.getHeight());
            puddleRect.setFill(Color.TRANSPARENT);
            puddles.add(puddleRect);
            StackPane puddleStack = new StackPane(puddleRect, puddleImg);
            map.getChildren().addAll(puddleStack);
            puddleStack.relocate(puddlesLocations.get(i).getX(), puddlesLocations.get(i).getY());
        }

        gameScene = new Scene(rootGame, W, H);

        gameScene.setOnKeyPressed(
                new EventHandler<KeyEvent>() {
                    @Override
                    public void handle(KeyEvent event) {
                        switch (event.getCode()) {
                            case W:
                                isDriving = true;
                                isIdle = false;
                                break;
                            case S:
                                isDriving = false;
                                isStopping = true;
                                isIdle = false;
                                break;
                            case A:

                                isTurning = true;
                                left = true;

                                break;
                            case D:
                                isTurning = true;
                                left = false;
                                break;
                            case SHIFT:
                                running = true;
                                break;
                        }
                    }
                });
        gameScene.setOnKeyReleased(
                new EventHandler<KeyEvent>() {
                    @Override
                    public void handle(KeyEvent event) {
                        switch (event.getCode()) {
                            case W:

                                isDriving = true;
                                isIdle = true;

                                break;
                            case S:
                                isStopping = false;
                                isIdle = true;
                                break;
                            case A:

                                isTurning = false;
                                left = false;

                                break;
                            case D:
                                isTurning = false;
                                left = false;
                                break;
                            case SHIFT:
                                running = false;
                                break;
                        }
                    }
                });

        AnimationTimer timer =
                new AnimationTimer() {

                    @Override
                    public void handle(long now) {
                        int dx = 0, dy = 0;

                        if (!cars.isEmpty()) {
                            if (mAcceleration < 0) {
                                isReversing = true;
                            } else {
                                isReversing = false;
                            }
                            rotation = cars.get(currentIndex).getRotate();
                            if (rotation > 360) {
                                rotation = rotation - 360.0 * (int) (rotation / 360);
                            }
                            if (isTurning && left && isDriving) {
                                rotateBy = -4;
                            } else if (isTurning && !left && isDriving) {
                                rotateBy = 4;
                            } else if (isTurning && left && isReversing) {
                                rotateBy = 4;
                            } else if (isTurning && !left && isReversing) {
                                rotateBy = -4;
                            } else {
                                rotateBy = 0;
                            }

                            if (isDriving && !isIdle) {

                                if (mAcceleration <= 0) {
                                    mAcceleration += 0.4;

                                } else if (mAcceleration < MAX_SPEED && mAcceleration > 0) {
                                    mAcceleration += 0.1;

                                }
                            } else if (isDriving && isIdle) {

                                if (mAcceleration > 0) {
                                    mAcceleration -= 0.2;
                                }
                            } else if (isStopping && !isIdle) {

                                if (mAcceleration > 0) {
                                    mAcceleration -= 0.4;
                                } else {
                                    mAcceleration -= 0.1;
                                }

                            } else if (!isDriving && isIdle && isReversing) {
                                mAcceleration += 0.1;
                            }
                            dy -= (float) Math.cos(Math.toRadians(rotation)) * mAcceleration;
                            dx += (float) Math.sin(Math.toRadians(rotation)) * mAcceleration;

                            moveCarBy(dx, dy, currentIndex, (int) (rotation + rotateBy));

                            /** Send data to the server */

                            if ((dx != 0 || dy != 0 || rotateBy != 0)) {
                                pwt.println("MOVE");
                                pwt.println(currentIndex);
                                pwt.println((int) cars.get(currentIndex).getLayoutX());
                                pwt.println((int) cars.get(currentIndex).getLayoutY());
                                pwt.println((int) (rotation + rotateBy));
                                pwt.flush();
                            }
                        }
                    }
                };
        timer.start();


        stage.setScene(mainMenuScene);
        stage.show();
    }

    @Override
    public void handle(ActionEvent actionEvent) {
        String command = ((Button) actionEvent.getSource()).getText();
        switch (command) {
            case "Connect":
                doConnect();
                stage.setScene(chatScene);
                break;

            case "Host":
                doHost();
                System.out.println("Server");
                stage.setScene(chatScene);
                break;

            case "Quit":
                doQuit();
                break;

            case "Start":
                doStart();
                break;
        }
    }
    
     /*
     *Starts game with start button
     */
    public void doStart() {
        stage.setScene(gameScene);
        for (ClientThread ct : clients) {
            ct.pwt.println("START");
            ct.pwt.flush();
        }
    }
    
    
   /*
   *client connect to server
   */
    public void doConnect() {
        try {
            TextInputDialog td = new TextInputDialog("localhost");
            td.setContentText("IP address:");
            Optional<String> res = td.showAndWait();
            if (res.isPresent()) {
                btnStart.setDisable(true);
                Socket socket = new Socket(td.getEditor().getText(), SERVER_PORT);

                stage.setTitle("Racer");
                scn = new Scanner(socket.getInputStream());
                pwt = new PrintWriter(socket.getOutputStream());
                chatLog = FXCollections.observableArrayList();

                ClientRunnable cr = new ClientRunnable();
                cr.start();
            }
        } catch (Exception e) {
        }
    }

    /*
   *hosts the server
   */
    public void doHost() {
        host = true;
        stage.setTitle("Host");
        ServerThread st = new ServerThread();
        st.start();
    }
    
    /*
   *quits the game
   */
    public void doQuit() {
        System.exit(1);
    }
   
    /*
    *chat message
   */
    public void sendMessage(String text) {
        pwt.println("MESSAGE");
        pwt.println("P" + currentIndex + ": " + text);
        pwt.flush();
    }

    class ClientRunnable extends Thread {
        public void run() {
            while (true) {
                String command = scn.nextLine();
                switch (command) {
                    case "END": {
                        int index = scn.nextInt();
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                lblLap.setVisible(false);
                                uiPane.getChildren().add(lblWinner);
                                uiPane.setAlignment(Pos.CENTER);
                                uiPane.setPrefHeight(H);
                                uiPane.setPrefWidth(W);
                                lblWinner.setAlignment(Pos.CENTER);
                                lblWinner.setStyle("-fx-font-size: 30pt; -fx-text-fill: white;");
                                lblWinner.setText("Racer "+ index + " won");
                                Timer timer = new Timer();
                                timer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        stage.setScene(chatScene);
                                    }
                                }, 3 * 1000);
                            }
                        });
                    }
                    case "GETMESSAGE": {
                        String text = scn.nextLine();
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                chatLog.add(text);
                                chatList.setItems(chatLog);
                            }
                        });
                        break;
                    }
                    case "START": {
                        Platform.runLater(() -> stage.setScene(gameScene));
                        break;
                    }
                    case "INSTANTIATE": {
                        currentIndex = scn.nextInt();
                        System.out.println("Client #" + currentIndex);
                        ImageView carImage = new ImageView(carImages.get(currentIndex));
                        javafx.scene.shape.Rectangle carRectangle = new javafx.scene.shape.Rectangle(carImages.get(currentIndex).getWidth(), carImages.get(currentIndex).getHeight());
                        carRectangle.setFill(Color.TRANSPARENT);
                        StackPane carStack = new StackPane(carRectangle, carImage);
                        carStack.relocate(243, 413);
                        cars.add(carStack);
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                map.getChildren().add(cars.get(currentIndex));
                            }
                        });

                        for (int i = 0; i < currentIndex; i++) {
                            final int urMomWeight = i;

                            carImage = new ImageView(carImages.get(i));
                            carRectangle = new javafx.scene.shape.Rectangle(carImages.get(i).getWidth(), carImages.get(i).getHeight());
                            carStack = new StackPane(carRectangle, carImage);
                            carStack.relocate(243, 413);
                            carRectangle.setFill(Color.TRANSPARENT);
                            cars.add(carStack);
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    map.getChildren().add(cars.get(urMomWeight));
                                }
                            });
                        }
                        break;
                    }
                    case "NEWCAR": {

                        int tempID = scn.nextInt();
                        System.out.println("New " + tempID);

                        ImageView carImage = new ImageView(carImages.get(tempID));
                        javafx.scene.shape.Rectangle carRectangle = new javafx.scene.shape.Rectangle(carImages.get(tempID).getWidth(), carImages.get(tempID).getHeight());
                        StackPane carStack = new StackPane(carRectangle, carImage);
                        carStack.relocate(243, 413);
                        carRectangle.setFill(Color.TRANSPARENT);
                        cars.add(carStack);

                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                map.getChildren().add(cars.get(tempID));
                            }
                        });
                        break;
                    }
                    case "MOVECAR": {
                        int ID = scn.nextInt();
                        int x = scn.nextInt();
                        int y = scn.nextInt();
                        int rotation = scn.nextInt();
                        moveCarTo(x, y, ID, rotation);
                        break;
                    }
                }
            }
        }
    }

    class ServerThread extends Thread {
        ServerSocket sSocket;

        public void run() {
            try {
                sSocket = new ServerSocket(6969);

                while (true) {

                    Socket cSocket = sSocket.accept();

                    ImageView carImage = new ImageView(carImages.get(index));
                    javafx.scene.shape.Rectangle carRectangle = new javafx.scene.shape.Rectangle(carImages.get(index).getWidth(), carImages.get(index).getHeight());
                    carRectangle.setFill(Color.TRANSPARENT);
                    StackPane carStack = new StackPane(carRectangle, carImage);
                    carStack.relocate(243, 413);
                    carRectangle.setFill(Color.TRANSPARENT);
                    cars.add(carStack);

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            map.getChildren().add(cars.get(index));
                        }
                    });

                    System.out.println("New client " + index);

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            chatLog.add("Client "
                                    + cSocket.getRemoteSocketAddress()
                                    + " connected");
                            chatList.setItems(chatLog);
                        }
                    });

                    ClientThread ct = new ClientThread(cSocket);
                    clients.add(ct);
                    ct.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class ClientThread extends Thread {
        public Socket cSocket;
        public Scanner scn;
        public PrintWriter pwt;

        public ClientThread(Socket _cSocket) {
            this.cSocket = _cSocket;
        }

        public void run() {
            try {

                this.scn = new Scanner(cSocket.getInputStream());
                this.pwt = new PrintWriter(cSocket.getOutputStream());

                pwt.println("INSTANTIATE");
                pwt.println(index);
                pwt.flush();

                for (ClientThread ct : clients) {
                    if (ct.cSocket != this.cSocket) {
                        ct.pwt.println("NEWCAR");
                        ct.pwt.println(index);
                        ct.pwt.flush();
                    }
                }

                index++;

                while (this.scn.hasNextLine()) {
                    String command = this.scn.nextLine();
                    switch (command) {
                        case "LAP": {
                            int index = scn.nextInt();
                            for (ClientThread ct : clients) {
                                ct.pwt.println("END");
                                ct.pwt.println(index);
                                ct.pwt.flush();
                            }
                            break;
                        }
                        case "MESSAGE": {
                            String message = scn.nextLine();
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    chatLog.add(message);
                                    chatList.setItems(chatLog);
                                }
                            });
                            for (ClientThread ct : clients) {
                                if (ct.cSocket != this.cSocket) {
                                    ct.pwt.println("GETMESSAGE");
                                    ct.pwt.println(message);
                                    ct.pwt.flush();
                                }
                            }
                            break;
                        }
                        case "MOVE": {
                            int ID = this.scn.nextInt();
                            int x = this.scn.nextInt();
                            int y = this.scn.nextInt();
                            int rotation = this.scn.nextInt();

                            for (ClientThread ct : clients) {
                                if (ct.cSocket != this.cSocket) {
                                    ct.pwt.println("MOVECAR");
                                    ct.pwt.println(ID);
                                    ct.pwt.println(x);
                                    ct.pwt.println(y);
                                    ct.pwt.println(rotation);
                                    ct.pwt.flush();
                                }
                            }
                            moveCarTo(x, y, ID, rotation);
                        }
                    }
                }
                cSocket.close();

            } catch (Exception e) {
            }
        }
    }

    public class XMLSettings {
        private String xmlFilePath = "";

        public XMLSettings(String filePath) {
            this.xmlFilePath = filePath;
        }

        public void readXML() {
            try {
                DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = dbfactory.newDocumentBuilder();
                XPathFactory xpfactory = XPathFactory.newInstance();
                XPath path = xpfactory.newXPath();

                File f = new File("settings.xml");
                Document doc = builder.parse(f);

                SERVER_PORT = Integer.parseInt(path.evaluate(
                        "/GameSettings/Socket/port", doc));

                MAX_SPEED = Integer.parseInt(path.evaluate(
                        "/GameSettings/Misc/maxSpeed", doc));

                MAX_PLAYERS = Integer.parseInt(path.evaluate(
                        "/GameSettings/Misc/maxPlayers", doc));

                MAX_LAPS = Integer.parseInt(path.evaluate(
                        "/GameSettings/Misc/maxLaps", doc));

            } catch (XPathExpressionException xpee) {
                System.out.println(xpee);
            } catch (ParserConfigurationException pce) {
                System.out.println(pce);
            } catch (SAXException saxe) {
                System.out.println(saxe);
            } catch (IOException ioe) {
                System.out.println(ioe);
            }
        }
    }
     
      /*
   *movement mathematics
   */
    private void moveCarBy(int dx, int dy, int index, int rotation) {

        if (dx == 0 && dy == 0)
            return;

        final double cx = cars.get(index).getBoundsInLocal().getWidth() / 2;
        final double cy = cars.get(index).getBoundsInLocal().getHeight() / 2;

        double x = cx + cars.get(index).getLayoutX() + dx;
        double y = cy + cars.get(index).getLayoutY() + dy;

        moveCarTo(x, y, index, rotation);
    }

    private void moveCarTo(double x, double y, int index, int rotation) {
        cars.get(index).setRotate(rotation);

        final double cx = cars.get(index).getBoundsInLocal().getWidth() / 2;
        final double cy = cars.get(index).getBoundsInLocal().getHeight() / 2;

        if (x - cx >= 0 && x + cx <= W && y - cy >= 0 && y + cy <= H) {
            cars.get(index).relocate(x - cx, y - cy);
        }

        Bounds pos = cars.get(index).localToScene(cars.get(index).getBoundsInLocal());

        int topleft = backg.getRGB((int) pos.getMinX(), (int) pos.getMaxY()) >> 24 & 0xff;
        int topright = backg.getRGB((int) pos.getMaxX(), (int) pos.getMaxY()) >> 24 & 0xff;
        int bottomright = backg.getRGB((int) pos.getMaxX(), (int) pos.getMinY()) >> 24 & 0xff;
        int bottomleft = backg.getRGB((int) pos.getMinX(), (int) pos.getMinY()) >> 24 & 0xff;

        if (topleft == 0 || topright == 0 || bottomright == 0 || bottomleft == 0) {
            mAcceleration = 0;
        }
        if (index == currentIndex && host == false) {
            Shape intersect = Shape.intersect((Shape) cars.get(currentIndex).getChildren().get(0), finishRect);
            if (intersect.getBoundsInLocal().getWidth() != -1 && newLapTimedOut) {
                numLaps++;
                newLapTimedOut = false;
                Platform.runLater(() -> lblLap.setText("LAP " + numLaps + "/" + MAX_LAPS));
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        newLapTimedOut = true;
                    }
                }, 10 * 1000);
                if (numLaps > MAX_LAPS) {
                    pwt.println("LAP");
                    pwt.println(currentIndex);
                    pwt.flush();
                }
            }
        }
        for (Shape puddle : puddles) {
            if (puddle != cars.get(index).getChildren().get(0)) {
                Shape intersect = Shape.intersect((Shape) cars.get(index).getChildren().get(0), puddle);
                if (intersect.getBoundsInLocal().getWidth() != -1) {
                    mAcceleration = 0.5F;
                }
            }
        }
    }
}