package com.sjtudoit.majiang.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.sjtudoit.majiang.client.AIMajiangClient;
import com.sjtudoit.majiang.client.AIMajiangClient2;
import com.sjtudoit.majiang.client.MajiangClient;
import com.sjtudoit.majiang.constant.RobotStatus;
import com.sjtudoit.majiang.dto.Game;
import com.sjtudoit.majiang.dto.Majiang;
import com.sjtudoit.majiang.dto.Message;
import com.sjtudoit.majiang.dto.User;
import com.sjtudoit.majiang.utils.MajiangUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import static com.sjtudoit.majiang.constant.MessageType.*;

@ServerEndpoint("/game/{name}")
@RestController
public class GameController {

    // 创建默认当前游戏
    public static List<Game> currentGameList = new ArrayList<>();

    // 创建三桌麻将
    static {
        for (int i = 0; i < 3; i++) {
            currentGameList.add(new Game(INFO));
        }
    }

    // 所有建立的websocket服务端连接集合
    public static Set<GameController> webSocketSet = new CopyOnWriteArraySet<>();

    // 机器人集合
    private static Set<MajiangClient> robotClientSet = new CopyOnWriteArraySet<>();

    // 当前所有建立连接的用户的sessionId和userName组成的userMap
    private static Map<String, String> userMap = new HashMap<>();

    // 连接对应的机器人
    private MajiangClient robotClient = null;

    // 本websocket连接对应的桌号
    private Integer tableId = null;

    // 与某个客户端的连接会话，需要通过它来与客户端进行数据收发
    private Session session;

    // 该会话的用户名
    private String sessionName;

    private static final Logger LOGGER = LoggerFactory.getLogger(GameController.class);

    @OnOpen
    public void onOpen(Session session, @PathParam("name") String name) throws Exception {
        this.session = session;
        this.sessionName = name;
        // 添加用户会话信息
        userMap.put(session.getId(), name);
        webSocketSet.add(this);
        for (MajiangClient majiangClient : robotClientSet) {
            if (majiangClient.getName().equals(name) && majiangClient.getRobotStatus() != RobotStatus.USER_ROBOT_OUT) {
                // 记录当前会话对应的机器人对象（如果存在）
                robotClient = majiangClient;
                tableId = majiangClient.getTableId();
                if (majiangClient.getRobotStatus() == RobotStatus.USER_ROBOT_IN) {
                    // 更改托管机器人状态，防止正常玩家进入时误认为是机器人
                    majiangClient.setRobotStatus(RobotStatus.USER_ROBOT_OUT);
                }
            }
        }
        // 防止一个用户建立两个连接
        for (GameController gameController : webSocketSet) {
            if (gameController != this && gameController.sessionName.equals(name) && gameController.robotClient == null) {
                LOGGER.info("用户{}进入大厅，但大厅中已有同名用户，故本次连接关闭", name);
                session.close();
                return;
            }
        }
        List<List<String>> userUserList = currentGameList.stream().map(game -> game.getUserList().stream().map(User::getUserNickName).collect(Collectors.toList())).collect(Collectors.toList());
        session.getBasicRemote().sendText(JSONObject.toJSONString(userUserList, SerializerFeature.DisableCircularReferenceDetect));
        LOGGER.info("用户{}" + (robotClient == null ? "" : "机器人") + "进入大厅，当前的userMap是{}，\r\n robotClientSet是{}, \r\n webSocketSet是{}", name, userMap, robotClientSet, webSocketSet);
    }

    @OnClose
    public void onClose(@PathParam("name") String name) throws Exception {
        // 连接关闭时确保相关对象均已释放
        webSocketSet.remove(this);
        userMap.remove(session.getId());
        Game currentGame = currentGameList.get(tableId);
        if (robotClient != null) {
            robotClientSet.remove(robotClient);
            if (currentGame.getGameStarted()) {
                LOGGER.info("用户{}机器人托管取消，退出大厅，当前的userMap是{}，\r\n robotClientSet是{}, \r\n webSocketSet是{}", name, userMap, robotClientSet, webSocketSet);
                return;
            }
        }
        if (tableId == null) {
            LOGGER.info("用户{}尚未选桌就直接退出大厅，当前的userMap是{}，\r\n robotClientSet是{}, \r\n webSocketSet是{}", name, userMap, robotClientSet, webSocketSet);
            return;
        }
        if (currentGame.getGameStarted()) {
            // 游戏正在进行中发生异常情况中止连接时，切换为托管模式
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            String robotName = name;
            MajiangClient client = new AIMajiangClient2(robotName, tableId);
            robotClientSet.add(client);
            container.connectToServer(client, new URI("ws://localhost:8080/game/" + URLEncoder.encode(robotName, "UTF-8")));
            for (User user : currentGame.getUserList()) {
                if (user.getUserNickName().equals(robotName)) {
                    user.setRobotPlay(true);
                }
            }
            currentGame.setMessageType(INFO);
            currentGame.setMessage(name + "开启托管");
            sendMessage(currentGame);
            LOGGER.info("{}异常退出，改为托管模式，当前的userMap是{}，\r\n robotClientSet是{}, \r\n webSocketSet是{}}", name, userMap, robotClientSet, webSocketSet);
            return;
        }
        // 一局游戏结束后，用户下线前删除牌桌内的信息
        List<User> userList = currentGame.getUserList();
        for (int i = 0; i < 4; i++) {
            User user = userList.get(i);
            if (name.equals(user.getUserNickName())) {
                user.setUserNickName("");
                user.setReady(false);
                // 广播用户下线
                currentGame.setMessageType(INFO);
                currentGame.setMessage(name + "退出房间");
                sendTableMessage(currentGame);
                break;
            }
        }

        // 删除用户会话信息
        LOGGER.info("用户{}" + (robotClient == null ? "" : "机器人") + "正常退出大厅，当前的userMap是{}，\r\n robotClientSet是{}, \r\n webSocketSet是{}", name, userMap, robotClientSet, webSocketSet);

        // 若场内只有机器人，则关闭所有玩家连接
        if (currentGame.getUserList().stream().allMatch(user -> user.getUserNickName().startsWith("玩家") || user.getUserNickName().isEmpty())) {
            List<String> userNameList = currentGame.getUserList().stream().map(User::getUserNickName).collect(Collectors.toList());
            currentGameList.set(tableId, new Game(INFO));
            for (GameController gameController : webSocketSet) {
                if (userNameList.contains(gameController.sessionName) && gameController.sessionName.startsWith("玩家")) {
                    gameController.session.close();
                }
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error){
        LOGGER.error("websocket服务端连接出错", error);
    }

    @OnMessage
    public void onMessage(String str, Session session) throws Exception {
        LOGGER.info("用户{}，信息{}，桌号{}", userMap.get(this.session.getId()), str, tableId == null ? "null" : tableId);
        Message receivedMessage = JSON.parseObject(str, Message.class);
        // 当前会话的用户名，理论上应该和currentGame.getCurrentUserName相同
        String sessionUserName = userMap.get(this.session.getId());

        // 发送文字消息
        if (receivedMessage.getType().equals(CHAT)) {
            sendMessage(receivedMessage);
            return;
        }

        // 发送语音消息
        if (receivedMessage.getType().equals(AUDIO_CHAT)) {
            sendMessage(receivedMessage);
            return;
        }

        if (receivedMessage.getType().equals(HEART_BEAT)) {
            // 心跳包不响应
            return;
        }

        // 玩家进入，选座位
        if (receivedMessage.getType().equals(CHOOSE_SEAT)) {
            // 将用户加入牌桌
            String position = receivedMessage.getMessage();
            tableId = Integer.valueOf(position) / 4;
            Game currentGame = currentGameList.get(tableId);
            List<User> userList = currentGame.getUserList();

            // 判断用户是否为重新回到房间进入游戏（之前由机器人托管代打）
            boolean resume = false;
            for (MajiangClient majiangClient : robotClientSet) {
                if (majiangClient.getName().equals(sessionUserName) && !sessionUserName.startsWith("玩家") && tableId.equals(majiangClient.getTableId())) {
                    // 用户重新进入时，机器人玩家下线
                    resume = true;
                    majiangClient.getSession().close();
                }
            }
            if (resume) {
                // 说明有机器人托管，需要退出机器人
                for (User user : currentGame.getUserList()) {
                    if (user.getUserNickName().equals(sessionUserName)) {
                        user.setRobotPlay(false);
                    }
                }
                LOGGER.info("{}重新回到房间{}选择座位，当前的userMap是{}，\r\n robotClientSet是{}, \r\n websocketSet是{}", sessionUserName, tableId, userMap, robotClientSet, webSocketSet);
                currentGame.setMessageType(INFO);
                currentGame.setMessage(sessionUserName + "进入房间");
                sendTableMessage(currentGame);
            } else {
                for (GameController gameController : webSocketSet) {
                    if (gameController.tableId != null && gameController.tableId.equals(tableId) && gameController.sessionName.equals(this.sessionName) && gameController != this) {
                        // 说明没有机器人，但是有同名者占用本桌连接位置，则把它踢了
                        LOGGER.info("{}进入房间并把上一个它的连接踢了，当前的userMap是{}，\r\n robotClientSet是{}, \r\n websocketSet是{}", sessionUserName, userMap, robotClientSet, webSocketSet);
                        gameController.tableId = null;
                        gameController.session.close();
                        currentGame.setMessageType(INFO);
                        currentGame.setMessage(sessionUserName + "进入房间");
                        sendTableMessage(currentGame);
                        return;
                    }
                }
            }

            if (userList.stream().filter(user -> !user.getUserNickName().equals("")).count() == 4) {
                // 如果牌桌上已经有4个人，则选择座位无效（防止机器人自动准备）
                return;
            }

            // 将用户加入牌桌
            if (Integer.valueOf(position) % 4 == 0) {
                for (int i = 0; i < 4; i++) {
                    User user = userList.get(i);
                    if (user.getUserNickName().equals("") || sessionUserName.equals(user.getUserNickName())) {
                        user.setIndex(i);
                        user.setUserNickName(sessionUserName);
                        break;
                    }
                }
            } else {
                User user = userList.get(Integer.valueOf(position) % 4);
                if (user.getUserNickName().equals("") || sessionUserName.equals(user.getUserNickName())) {
                    user.setIndex(Integer.valueOf(position) % 4);
                    user.setUserNickName(sessionUserName);
                }
            }
            // 广播用户进入房间
            currentGame.setMessageType(INFO);
            currentGame.setMessage(sessionUserName + "进入房间");
            sendTableMessage(currentGame);
            return;
        }

        if (tableId == null) {
            return;
        }
        Game currentGame = currentGameList.get(tableId);

        // 获取当前游戏信息
        if (receivedMessage.getType().equals(GET_GAME)) {
            if (session.isOpen()) {
                session.getBasicRemote().sendText(JSONObject.toJSONString(currentGame, SerializerFeature.DisableCircularReferenceDetect));
            }
            return;
        }

        // 添加机器人
        if (receivedMessage.getType().equals(ADD_ROBOT)) {
            if (currentGame.getUserList().stream().filter(user -> !user.getUserNickName().equals("")).count() == 4) {
                return;
            }
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            String robotName;
            MajiangClient client;
            if (robotClientSet.size() % 3 == 2) {
                robotName = "玩家" + (robotClientSet.size() + 1);
                client = new MajiangClient(robotName);
            } else if (robotClientSet.size() % 3 == 1) {
                robotName = "玩家高级" + (robotClientSet.size() + 1);
                client = new AIMajiangClient(robotName);
            } else {
                robotName = "玩家特级" + (robotClientSet.size() + 1);
                client = new AIMajiangClient2(robotName);
            }
            /*robotName = "玩家特级" + (robotClientSet.size() + 1);
            client = new AIMajiangClient2(robotName);*/
            robotClientSet.add(client);
            container.connectToServer(client, new URI("ws://localhost:8080/game/" + URLEncoder.encode(robotName, "UTF-8")));
            client.send(new Message(CHOOSE_SEAT, String.valueOf(tableId * 4)));
            return;
        }

        // 玩家退出
        if (receivedMessage.getType().equals(QUIT)) {
            // 用户下线前删除牌桌内的信息
            List<User> userList = currentGame.getUserList();
            for (int i = 0; i < 4; i++) {
                User user = userList.get(i);
                if (sessionUserName.equals(user.getUserNickName())) {
                    if (!currentGame.getGameStarted()) {
                        user.setReady(false);
                        user.setUserNickName("");
                        break;
                    } else {
                        // 用户在游戏进行中退出，则直接关闭websocket连接，会触发托管模式
                        session.close();
                    }
                }
            }

            // 广播用户下线
            currentGame.setMessageType(INFO);
            currentGame.setMessage(sessionUserName + "退出房间");
            sendTableMessage(currentGame);

            // 当用户人数为0时重新开始游戏，并清空所有音频信息
            if (currentGame.getUserList().stream().allMatch(user -> user.getUserNickName().equals(""))) {
                currentGameList.set(tableId, new Game(INFO));
                String filePath = ClassUtils.getDefaultClassLoader().getResource("").getPath() + "static/audio/";
                try {
                    File file = new File(filePath);
                    if(file.exists()) {
                        File[] filePaths = file.listFiles();
                        for(File f : filePaths) {
                            if(f.isFile()) {
                                f.delete();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return;
        }

        // 响应准备操作
        if (receivedMessage.getType().equals(CLIENT_READY)) {
            List<User> userList = currentGame.getUserList();
            for (int i = 0; i < 4; i++) {
                User user = userList.get(i);
                if (sessionUserName.equals(user.getUserNickName())) {
                    user.setReady(true);

                    // 广播通知某某用户已准备
                    currentGame.setMessageType(CLIENT_READY);
                    currentGame.setMessage(sessionUserName);
                    sendMessage(currentGame);
                    if (userList.stream().filter(User::getReady).count() == 4) {
                        // 如果4个人都准备则游戏开始
                        receivedMessage.setType(START_GAME);
                        break;
                    } else {
                        return;
                    }
                }
            }
        }

        // 响应游戏开始指令
        if (receivedMessage.getType().equals(START_GAME)) {
            Game game;
            if (currentGame.getRemainMajiangList().size() > 0) {
                // 剩余麻将大于0，说明是接着上一次的游戏继续
                game = MajiangUtil.newGame(currentGame.getBankerName(), currentGame.isBankerChange() ? currentGame.getBankerNo() + 1 : currentGame.getBankerNo(), currentGame.getUserList());
            } else {
                // 游戏刚开始，随机选择一个玩家做为庄家
                int index = new Random().nextInt(3);
                String bankerName = currentGame.getUserList().get(index).getUserNickName();
                game = MajiangUtil.newGame(bankerName, 1, currentGame.getUserList());
            }
            game.setMessageType(START_GAME);
            game.setGameStarted(true);
            currentGame = game;
            currentGameList.set(tableId, currentGame);

            // 游戏开始后设置全体用户状态为未准备，方便前端显示，等下次准备时再设为已准备
            currentGame.setUnReady();
            sendMessage(currentGame);
            return;
        }

        // 响应游戏中的其它指令

        // 余牌列表
        List<Majiang> remainMajiangList = currentGame.getRemainMajiangList();
        // 当前玩家列表
        List<User> userList = currentGame.getUserList();
        String currentUserName = currentGame.getCurrentUserName();
        String physicalNextUserName = currentGame.getPhysicalNextUserName();
        String bankerName = currentGame.getBankerName();
        // 当前轮到的玩家
        User currentUser;
        List<User> list = userList.stream().filter(user -> user.getUserNickName().equals(currentUserName)).collect(Collectors.toList());
        if (list.size() > 0) {
            currentUser = list.get(0);
        } else {
            return;
        }
        // 按照座位顺序应该轮到的玩家
        User physicalNextUser;
        // 上一个出牌的玩家
        User lastUser = new User();
        // 吃、碰、杠、暗杠、胡才要用到的
        if (receivedMessage.getType() >= 6 && receivedMessage.getType() <= 11) {
            physicalNextUser = userList.stream().filter(user -> user.getUserNickName().equals(physicalNextUserName)).collect(Collectors.toList()).get(0);
            lastUser = userList.get((physicalNextUser.getIndex() + 4 - 1) % 4);
        }

        if (!currentGame.getGameStarted()) {
            // 如果走到这一步当前局已结束，则不执行后续逻辑
            return;
        }
        // 防止不同用户之间的误操作，只有抢金指令可以无视当前轮到用户
        if (!Objects.equals(receivedMessage.getMessage(), "抢金") && !currentUserName.equals(sessionUserName)) {
            LOGGER.info("指令无效，此时轮到的不是{}", sessionUserName);
            return;
        }
        // 根据发送的指令信息响应同样的信息类型
        currentGame.setMessageType(receivedMessage.getType());
        switch (receivedMessage.getType()) {
            case RESET_FLOWER: {
                // 游戏开始时补花
                currentUser.resetFlower(remainMajiangList);
                // 更新补花者信息
                userList.set(currentUser.getIndex(), currentUser);

                boolean anyoneHasFlower = userList.stream().anyMatch(user -> user.getUserMajiangList().stream().anyMatch(majiang -> majiang.getCode() > 30));
                LOGGER.info("有否还有人要补花：" + anyoneHasFlower);

                User nextUser = new User();

                if (anyoneHasFlower) {
                    for (int i = 1; i <= 4; i++) {
                        nextUser = userList.get((currentUser.getIndex() + i) % 4);
                        if (nextUser.getUserMajiangList().stream().anyMatch(majiang -> majiang.getCode() > 30)) {
                            break;
                        }
                    }
                    // 下一个没花的补花
                    currentGame.setCurrentUserName(nextUser.getUserNickName());
                } else {
                    // 大家补花完了，直接轮到庄家
                    currentGame.setCurrentUserName(currentGame.getBankerName());

                    // 如果说已经开金，则不允许再次开金，该请求无效（防止因为网络问题多次请求造成多次开金）
                    if (currentGame.getJin() != null) {
                        return;
                    }

                    // --------------------------- 设定vip用户每局都有金的代码（开始） ------------------------------
                    User vipUser = null;
                    try {
                        vipUser = userList.stream().filter(user -> user.getUserNickName().equals("真遇")).collect(Collectors.toList()).get(0);
                    } catch (Exception e) {
                        LOGGER.error("vip用户不存在");
                    }
                    // 需要在vip用户存在且玩家开头的机器人不存在时方生效
                    if (vipUser != null && userList.stream().noneMatch(user -> user.getUserNickName().startsWith("玩家"))) {
                        // 先把花去掉
                        Iterator<Majiang> iterator = remainMajiangList.iterator();
                        Majiang first = remainMajiangList.get(0); // 用来调换的麻将
                        while (iterator.hasNext()) {
                            Majiang next = iterator.next();
                            if (next.getCode() < 30) {
                                first = next;
                                break;
                            }
                        }
                        Integer code = first.getCode();
                        if (vipUser.getUserMajiangList().stream().anyMatch(majiang -> majiang.getCode().equals(code))) {
                            LOGGER.info("本来应该开的金是{}，调换后开的金是，哦不，vip用户有金，不用调换啦", first.getName());
                        } else {
                            while (iterator.hasNext()) {
                                Majiang next = iterator.next();
                                if (next.getCode() < 30 && vipUser.getUserMajiangList().stream().anyMatch(majiang -> majiang.getCode().equals(next.getCode()))) {
                                    // 调换两个麻将的顺序
                                    LOGGER.info("本来应该开的金是{}，调换后开的金是{}", first.getName(), next.getName());
                                    int oldId = first.getId();
                                    int oldCope = first.getCode();
                                    String oldName = first.getName();
                                    first.setId(next.getId());
                                    first.setCode(next.getCode());
                                    first.setName(next.getName());
                                    next.setId(oldId);
                                    next.setCode(oldCope);
                                    next.setName(oldName);
                                    break;
                                }
                            }
                        }
                    }
                    // --------------------------- 设定vip用户每局都有金的代码（结束） ------------------------------

                    // 开金
                    Majiang jin = remainMajiangList.remove(0);
                    User banker = userList.stream().filter(user -> user.getUserNickName().equals(bankerName)).collect(Collectors.toList()).get(0);
                    while (jin.getCode() > 30) {
                        // 开金若是花则加到庄家的花中
                        banker.addFlowerList(jin);
                        jin = remainMajiangList.remove(0);
                    }
                    // 更新庄家信息
                    userList.set(banker.getIndex(), banker);

                    // 设置麻将为金
                    jin.setJin(true);
                    currentGame.setJin(jin);
                    for (int i = 0; i < remainMajiangList.size(); i++) {
                        if (remainMajiangList.get(i).getCode().equals(jin.getCode())) {
                            Majiang majiangJin = remainMajiangList.get(i);
                            majiangJin.setJin(true);
                            // remainMajiangList.set(i, majiangJin);
                        }
                    }

                    for (int i = 0; i < userList.size(); i++) {
                        User user = userList.get(i);
                        user.setJin(jin.getCode());
                        user.sortMajiangList();

                        // 判断是否能抢金
                        if (user.getUserNickName().equals(banker.getUserNickName())) {
                            // 庄家抢金判断
                            boolean canQiangJin = false;
                            for (int j = 0; j < user.getUserMajiangList().size(); j++) {
                                List<Majiang> majiangList = new ArrayList<>(user.getUserMajiangList());
                                majiangList.remove(j);
                                if (MajiangUtil.canHuWithQiangJin(majiangList, jin)) {
                                    canQiangJin = true;
                                    break;
                                }
                            }
                            LOGGER.info("庄家的牌能否抢金：" + canQiangJin);
                            user.setCanQiangJin(canQiangJin);
                        } else {
                            // 其它玩家抢金判断
                            boolean canQiangJin = MajiangUtil.canHuWithQiangJin(user.getUserMajiangList(), jin);
                            LOGGER.info(user.getUserNickName() + "的牌能否抢金：" + canQiangJin);
                            user.setCanQiangJin(canQiangJin);
                        }
                    }

                    // 判断抢金顺序
                    List<String> nextUserNameList = new ArrayList<>();
                    for (int i = 1; i <= 4; i++) {
                        User user = userList.get((banker.getIndex() + i) % 4);
                        if (user.getCanQiangJin()) {
                            // 依次判断是否能抢金，并确定抢金顺序，若都能抢金，则庄家最后抢
                            nextUserNameList.add(user.getUserNickName());
                        }
                    }

                    if (nextUserNameList.size() > 0) {
                        String nextUserName = nextUserNameList.iterator().next();
                        nextUserNameList.remove(nextUserName);
                        // 根据规则跳转至下家
                        currentGame.setCurrentUserName(nextUserName);
                    }
                    currentGame.setNextUserNameList(nextUserNameList);
                }

                // 更新游戏信息
                currentGame.setRemainMajiangList(remainMajiangList);
                sendMessage(currentGame);
                break;
            }
            case MJ_OUT: {
                if (currentUser.getUserMajiangList().stream().filter(majiang -> !majiang.isShow() && !majiang.isAnGang()).count() % 3 != 2) {
                    // 防止网络延时的误操作
                    LOGGER.info("指令无效，不能再出牌");
                    return;
                }
                // 用户出牌
                String index = receivedMessage.getMessage();
                Majiang currentOutMajiang = currentUser.removeMajiang(Integer.valueOf(index));

                currentUser.sortMajiangList();
                User nextUser = userList.get((currentUser.getIndex() + 1) % 4);
                User secondUser = userList.get((currentUser.getIndex() + 2) % 4);
                User thirdUser = userList.get((currentUser.getIndex() + 3) % 4);

                List<String> nextUserNameList = new ArrayList<>();
                if (!currentOutMajiang.isJin()) {
                    // 打出的牌不是金时，根据金雀、胡、碰（杠）、吃的优先级判断下家
                    if (MajiangUtil.canJinQueWithNewMajiang(nextUser.getUserMajiangList(), currentOutMajiang)) {
                        nextUserNameList.add(nextUser.getUserNickName());
                    }
                    if (MajiangUtil.canJinQueWithNewMajiang(secondUser.getUserMajiangList(), currentOutMajiang)) {
                        nextUserNameList.add(secondUser.getUserNickName());
                    }
                    if (MajiangUtil.canJinQueWithNewMajiang(thirdUser.getUserMajiangList(), currentOutMajiang)) {
                        nextUserNameList.add(thirdUser.getUserNickName());
                    }

                    if (MajiangUtil.canHuWithNewMajiang(nextUser.getUserMajiangList(), currentOutMajiang)) {
                        nextUserNameList.add(nextUser.getUserNickName());
                    }
                    if (MajiangUtil.canHuWithNewMajiang(secondUser.getUserMajiangList(), currentOutMajiang)) {
                        nextUserNameList.add(secondUser.getUserNickName());
                    }
                    if (MajiangUtil.canHuWithNewMajiang(thirdUser.getUserMajiangList(), currentOutMajiang)) {
                        nextUserNameList.add(thirdUser.getUserNickName());
                    }

                    if (MajiangUtil.canPeng(nextUser.getUserMajiangList(), currentOutMajiang.getCode())) {
                        nextUserNameList.add(nextUser.getUserNickName());
                    } else if (MajiangUtil.canPeng(secondUser.getUserMajiangList(), currentOutMajiang.getCode())) {
                        nextUserNameList.add(secondUser.getUserNickName());
                    } else if (MajiangUtil.canPeng(thirdUser.getUserMajiangList(), currentOutMajiang.getCode())) {
                        nextUserNameList.add(thirdUser.getUserNickName());
                    }

                    if (nextUserNameList.size() > 0) {
                        Iterator<String> iterator = nextUserNameList.iterator();
                        String nextUserName = iterator.next();
                        nextUserNameList.remove(nextUserName);
                        // 根据规则跳转至下家
                        currentGame.setCurrentUserName(nextUserName);
                    } else {
                        // 无人能碰杠胡，则跳转到正常下家
                        currentGame.setCurrentUserName(nextUser.getUserNickName());
                    }
                }

                // 更新游戏信息，设置当前进的麻将为空
                currentGame.setNextUserNameList(nextUserNameList);
                currentGame.setPhysicalNextUserName(nextUser.getUserNickName());
                currentGame.setCurrentInMajiang(null);
                currentGame.setCurrentOutMajiang(currentOutMajiang);
                sendMessage(currentGame);
                break;
            }
            case MJ_IN: {
                // 判断是否和局
                if (remainMajiangList.size() <= 16) {
                    currentGame.setCurrentInMajiang(null);
                    currentGame.setMessageType(MJ_TIE);
                    MajiangUtil.countTie(userList);
                    sendMessage(currentGame);
                    return;
                }
                if (currentUser.getUserMajiangList().stream().filter(majiang -> !majiang.isShow() && !majiang.isAnGang()).count() % 3 != 1) {
                    // 防止网络延时的误操作
                    LOGGER.info("指令无效，不能再摸牌");
                    return;
                }
                // 用户请求摸牌
                Majiang currentInMajiang = remainMajiangList.remove(0);
                currentUser.addMajiang(currentInMajiang);

                currentGame.setRemainMajiangList(remainMajiangList);
                currentGame.setCurrentInMajiang(currentInMajiang);
                currentGame.setCurrentOutMajiang(null);
                sendMessage(currentGame);
                break;
            }
            case MJ_ADD_FLOWER: {
                // 判断是否和局
                if (remainMajiangList.size() <= 16) {
                    currentGame.setCurrentInMajiang(null);
                    currentGame.setMessageType(MJ_TIE);
                    MajiangUtil.countTie(userList);
                    sendMessage(currentGame);
                    return;
                }
                // 摸牌时摸到花，则补花（肯定在最后一个）
                Majiang currentOutMajiang = currentUser.getUserMajiangList().remove((currentUser.getUserMajiangList().size() - 1));
                currentUser.addFlowerList(currentOutMajiang);

                Majiang currentInMajiang = remainMajiangList.remove(remainMajiangList.size() - 1);
                currentUser.addMajiang(currentInMajiang);

                // 更新游戏信息，但不需要跳到下一个人
                userList.set(currentUser.getIndex(), currentUser);
                currentGame.setRemainMajiangList(remainMajiangList);
                currentGame.setCurrentInMajiang(currentInMajiang);
                sendMessage(currentGame);
                break;
            }
            case MJ_CHI: {
                String eatIds = receivedMessage.getMessage();
                String[] ids = eatIds.split(" ");
                Integer id1 = Integer.valueOf(ids[0]);
                Integer id2 = Integer.valueOf(ids[1]);
                Integer id3 = Integer.valueOf(ids[2]);

                // 防止网络不好时误操作
                if (currentUser.getUserMajiangList().stream().anyMatch(majiang -> majiang.isShow() && majiang.getId().equals(id1))) {
                    System.out.println("已经吃牌，不能重复吃");
                    return;
                }

                lastUser.removeLastOfOutList();

                Majiang currentOutMajiang = currentGame.getCurrentOutMajiang();
                currentUser.addMajiang(currentOutMajiang);
                currentUser.sortMajiangList();

                // 设置吃牌，id传入时为从小到大排列
                currentUser.setMajiangChi(id1, id2, id3);

                currentUser.sortMajiangList();

                // 更新游戏信息
                userList.set(currentUser.getIndex(), currentUser);
                currentGame.setCurrentInMajiang(null);
                currentGame.setCurrentOutMajiang(null);
                sendMessage(currentGame);
                break;
            }
            case MJ_PENG: {
                Majiang currentOutMajiang = currentGame.getCurrentOutMajiang();
                if (currentOutMajiang == null) {
                    System.out.println("当前无出牌，碰牌操作无效");
                    return;
                }

                Integer code = currentOutMajiang.getCode();

                // 防止网络不好时误操作
                if (currentUser.getUserMajiangList().stream().filter(majiang -> majiang.isShow() && majiang.getCode().equals(code)).count() == 3) {
                    System.out.println("已经碰牌，不能重复碰");
                    return;
                }

                lastUser.removeLastOfOutList();

                currentUser.addMajiang(currentOutMajiang);
                currentUser.sortMajiangList();

                // 碰牌，三张牌为一样
                currentUser.setMajiangPeng(code);

                currentUser.sortMajiangList();

                // 更新游戏信息
                userList.set(currentUser.getIndex(), currentUser);
                currentGame.setCurrentUserName(sessionUserName);
                currentGame.setCurrentInMajiang(null);
                currentGame.setCurrentOutMajiang(null);
                currentGame.setNextUserNameList(new ArrayList<>());
                sendMessage(currentGame);
                break;
            }
            case MJ_GANG: {
                Majiang currentOutMajiang = currentGame.getCurrentOutMajiang();
                if (currentOutMajiang == null) {
                    System.out.println("当前无出牌，杠牌操作无效");
                    return;
                }
                Integer code = currentOutMajiang.getCode();

                // 防止网络不好时误操作
                if (currentUser.getUserMajiangList().stream().filter(majiang -> majiang.isShow() && majiang.getCode().equals(code)).count() == 4) {
                    System.out.println("已经杠牌，不能重复杠");
                    return;
                }

                lastUser.removeLastOfOutList();

                currentUser.addMajiang(currentOutMajiang);
                currentUser.sortMajiangList();

                // 碰牌，三张牌为一样
                currentUser.setMajiangGang(code);

                currentUser.sortMajiangList();

                // 补花
                Majiang currentInMajiang = remainMajiangList.remove(remainMajiangList.size() - 1);
                currentUser.addMajiang(currentInMajiang);

                // 更新游戏信息
                currentGame.setRemainMajiangList(remainMajiangList);
                currentGame.setCurrentUserName(sessionUserName);
                currentGame.setCurrentOutMajiang(null);
                currentGame.setCurrentInMajiang(currentInMajiang);
                userList.set(currentUser.getIndex(), currentUser);
                currentGame.setNextUserNameList(new ArrayList<>());
                sendMessage(currentGame);
                break;
            }
            case MJ_AN_GANG: {
                String code = receivedMessage.getMessage();

                // 防止网络不好时误操作
                if (currentUser.getUserMajiangList().stream().filter(majiang -> majiang.isAnGang() && majiang.getCode().equals(Integer.valueOf(code))).count() == 4) {
                    System.out.println("已经暗杠，不能重复暗杠");
                    return;
                }

                currentUser.sortMajiangList();
                currentUser.setMajiangAnGang(Integer.valueOf(code));
                currentUser.sortMajiangList();

                // 补花
                Majiang currentInMajiang = remainMajiangList.remove(remainMajiangList.size() - 1);
                currentUser.addMajiang(currentInMajiang);

                // 更新游戏信息
                currentGame.setRemainMajiangList(remainMajiangList);
                currentGame.setCurrentInMajiang(currentInMajiang);
                currentGame.setCurrentOutMajiang(null);
                userList.set(currentUser.getIndex(), currentUser);
                sendMessage(currentGame);
                break;
            }
            case MJ_JIA_GANG: {
                String code = receivedMessage.getMessage();

                // 防止网络不好时误操作
                if (currentUser.getUserMajiangList().stream().filter(majiang -> majiang.isShow() && majiang.getCode().equals(Integer.valueOf(code))).count() == 4) {
                    System.out.println("已经加杠，不能重复加杠");
                    return;
                }

                currentUser.setMajiangJiaGang(Integer.valueOf(code));
                currentUser.sortMajiangList();

                // 补花
                Majiang currentInMajiang = remainMajiangList.remove(remainMajiangList.size() - 1);
                currentUser.addMajiang(currentInMajiang);

                // 更新游戏信息
                currentGame.setRemainMajiangList(remainMajiangList);
                userList.set(currentUser.getIndex(), currentUser);
                currentGame.setCurrentOutMajiang(null);
                currentGame.setCurrentInMajiang(currentInMajiang);
                sendMessage(currentGame);
                break;
            }
            case MJ_HU: {
                String message = receivedMessage.getMessage();
                Majiang currentOutMajiang = currentGame.getCurrentOutMajiang();
                // 庄家
                User banker = userList.stream().filter(user -> user.getUserNickName().equals(bankerName)).collect(Collectors.toList()).get(0);

                if ("抢金".equals(message)) {
                    // 判断是否抢金，由于庄家抢金时不按顺序来
                    User qiangJinUser = userList.stream().filter(user -> user.getUserNickName().equals(sessionUserName)).collect(Collectors.toList()).get(0);
                    List<Majiang> majiangList = qiangJinUser.getUserMajiangList();
                    if (majiangList.size() == 16 && MajiangUtil.canHuWithQiangJin(majiangList, currentGame.getJin())) {
                        qiangJinUser.addMajiang(currentGame.getJin());
                        qiangJinUser.sortMajiangList();

                        // 分数为20+2*(5+花+暗杠数+金)
                        int moneyNum = MajiangUtil.calculateScore(qiangJinUser, "抢金", currentGame);
                        qiangJinUser.setMajiangHu();

                        // 更新游戏信息
                        currentGame.setCurrentUserName(sessionUserName);
                        // 设置下轮庄家名称
                        currentGame.setBankerChange(!currentGame.getBankerName().equals(qiangJinUser.getUserNickName()));
                        currentGame.setBankerName(currentGame.getBankerName().equals(qiangJinUser.getUserNickName()) ? qiangJinUser.getUserNickName() : userList.get((banker.getIndex() + 1) % 4).getUserNickName());
                        // 设置抢金后不能抢金
                        qiangJinUser.setCanQiangJin(false);
                        userList.set(qiangJinUser.getIndex(), qiangJinUser);
                        MajiangUtil.countScore(userList, qiangJinUser, moneyNum);
                        sendMessage(currentGame);
                    }
                }

                if (currentUser.needAddMajiang()) {
                    // 用户的麻将数差1，还需要加一张才能胡
                    LOGGER.info("判断是否平胡");
                    if (currentOutMajiang == null) {
                        System.out.println("当前无出牌，胡牌操作无效");
                        return;
                    }
                    boolean canHu = MajiangUtil.canHuWithNewMajiang(currentUser.getUserMajiangList(), currentOutMajiang);
                    System.out.println(canHu);
                    if (canHu) {
                        lastUser.removeLastOfOutList();
                        currentUser.addMajiang(currentOutMajiang);
                        currentUser.sortMajiangList();

                        // 分数为5+花+暗杠数+金
                        int moneyNum = MajiangUtil.calculateScore(currentUser, "平胡", currentGame);
                        currentUser.setMajiangHu();

                        // 更新游戏信息
                        userList.set(currentUser.getIndex(), currentUser);
                        MajiangUtil.countScore(userList, currentUser, moneyNum);
                        currentGame.setBankerChange(!currentGame.getBankerName().equals(currentUser.getUserNickName()));
                        currentGame.setBankerName(currentGame.getBankerName().equals(currentUser.getUserNickName()) ? currentUser.getUserNickName() : userList.get((banker.getIndex() + 1) % 4).getUserNickName());
                        currentGame.setCurrentOutMajiang(null);
                        currentGame.setCurrentUserName(sessionUserName);
                        sendMessage(currentGame);
                    }
                } else {
                    LOGGER.info("判断是否自摸");
                    boolean canHu = MajiangUtil.canHu(currentUser.getUserMajiangList(), true);
                    System.out.println(canHu);
                    if (canHu) {
                        currentUser.sortMajiangList();

                        // 分数为2*（5+花+暗杠数+金+占庄数）
                        int moneyNum;
                        if (currentGame.getCurrentInMajiang() == null) {
                            // 如果是点了吃碰后再点的胡，则currentInMajiang为空，此处算分时应纠正为平胡
                            moneyNum = MajiangUtil.calculateScore(currentUser, "平胡", currentGame);
                        } else {
                            moneyNum = MajiangUtil.calculateScore(currentUser, "自摸", currentGame);
                        }
                        currentUser.setMajiangHu();

                        // 更新游戏信息
                        userList.set(currentUser.getIndex(), currentUser);
                        MajiangUtil.countScore(userList, currentUser, moneyNum);
                        currentGame.setBankerChange(!currentGame.getBankerName().equals(currentUser.getUserNickName()));
                        currentGame.setBankerName(currentGame.getBankerName().equals(currentUser.getUserNickName()) ? currentUser.getUserNickName() : userList.get((banker.getIndex() + 1) % 4).getUserNickName());
                        currentGame.setCurrentUserName(sessionUserName);
                        sendMessage(currentGame);
                    }
                }
                break;
            }
            case PASS: {
                // 处理过的逻辑（对于放弃碰杠胡等情况）
                List<String> nextUserNameList = currentGame.getNextUserNameList();
                if (nextUserNameList.size() > 0) {
                    Iterator<String> iterator = nextUserNameList.iterator();
                    String nextUserName = iterator.next();
                    nextUserNameList.remove(nextUserName);
                    currentGame.setNextUserNameList(nextUserNameList);
                    currentGame.setCurrentUserName(nextUserName);
                    sendMessage(currentGame);
                } else {
                    // 大家都过，则跳转到正常下家
                    currentGame.setCurrentUserName(currentGame.getPhysicalNextUserName());
                    sendMessage(currentGame);
                }
                break;
            }
            case GAME_OVER: {
                currentGame.setGameStarted(false);
                sendMessage(currentGame);
                break;
            }
        }
    }

    /**
     * 通过websocket发送游戏消息
     * @param game 当前游戏对象
     * @throws Exception
     */
    public void sendMessage(Game game) throws Exception {
        for (GameController gameController : webSocketSet) {
            if (gameController.tableId != null && gameController.tableId.equals(tableId) && gameController.session.isOpen()) {
                synchronized (gameController.session.getId()) {
                    // SerializerFeature.DisableCircularReferenceDetect: 避免fastjson解析对象时出现循环引用$ref
                    gameController.session.getBasicRemote().sendText(JSONObject.toJSONString(game, SerializerFeature.DisableCircularReferenceDetect));
                }
            }
        }
    }

    /**
     * 通过websocket发送大厅消息（给本桌之外的发送）
     * @throws Exception
     */
    public void sendTableMessage(Game currentGame) throws Exception {
        List<List<String>> userUserList = currentGameList.stream().map(game -> game.getUserList().stream().map(User::getUserNickName).collect(Collectors.toList())).collect(Collectors.toList());
        for (GameController gameController : webSocketSet) {
            if (gameController.session.isOpen()) {
                if (currentGame.getMessage().contains("退出")) {
                    if (gameController.tableId != null && gameController.tableId.equals(tableId) && gameController != this) {
                        // 向本桌内部连接发送currentGame
                        synchronized (gameController.session.getId()) {
                            gameController.session.getBasicRemote().sendText(JSONObject.toJSONString(currentGame, SerializerFeature.DisableCircularReferenceDetect));
                        }
                    } else {
                        // 向本桌之外的发送userUserList信息
                        synchronized (gameController.session.getId()) {
                            gameController.session.getBasicRemote().sendText(JSONObject.toJSONString(userUserList, SerializerFeature.DisableCircularReferenceDetect));
                        }
                    }
                } else {
                    if (gameController.tableId != null && gameController.tableId.equals(tableId)) {
                        // 向本桌内部连接发送currentGame
                        synchronized (gameController.session.getId()) {
                            gameController.session.getBasicRemote().sendText(JSONObject.toJSONString(currentGame, SerializerFeature.DisableCircularReferenceDetect));
                        }
                    } else {
                        // 向本桌之外的发送userUserList信息
                        synchronized (gameController.session.getId()) {
                            gameController.session.getBasicRemote().sendText(JSONObject.toJSONString(userUserList, SerializerFeature.DisableCircularReferenceDetect));
                        }
                    }
                }
            }
        }
    }

    /**
     * 通过websocket发送聊天消息
     * @param message 当前消息对象
     * @throws Exception
     */
    public void sendMessage(Message message) throws Exception {
        for (GameController gameController : webSocketSet) {
            if (gameController.tableId != null && gameController.tableId.equals(tableId) && gameController.session.isOpen()) {
                synchronized (gameController.session.getId()) {
                    gameController.session.getBasicRemote().sendText(JSONObject.toJSONString(message, SerializerFeature.DisableCircularReferenceDetect));
                }
            }
        }
    }

    public MajiangClient getRobotClient() {
        return robotClient;
    }

    public Session getSession() {
        return session;
    }

    @Override
    public String toString() {
        return "GameController{" +
                "robotClient=" + (robotClient == null ? "" : robotClient.getName()) +
                ", session=" + session.getId() +
                '}';
    }
}
