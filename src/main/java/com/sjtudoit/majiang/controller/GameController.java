package com.sjtudoit.majiang.controller;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.sjtudoit.majiang.dto.Game;
import com.sjtudoit.majiang.dto.Majiang;
import com.sjtudoit.majiang.dto.Message;
import com.sjtudoit.majiang.dto.User;
import com.sjtudoit.majiang.utils.MajiangUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import static com.sjtudoit.majiang.constant.MessageType.*;


@ServerEndpoint("/game/{name}")
@RestController
public class GameController {

    // 用来记录当前连接数的变量
    private static volatile int onlineCount = 0;

    // 创建默认当前游戏
    private static Game currentGame = new Game(INFO);

    // concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象
    private static CopyOnWriteArraySet<GameController> webSocketSet = new CopyOnWriteArraySet<>();

    // 当前用户Map
    private static Map<String, String> userMap = new HashMap<>();

    // 与某个客户端的连接会话，需要通过它来与客户端进行数据收发
    private Session session;

    private static final Logger LOGGER = LoggerFactory.getLogger(GameController.class);

    @OnOpen
    public void onOpen(Session session, @PathParam("name") String name) throws Exception {
        this.session = session;
        LOGGER.info("用户{}进入房间", name);
        if (userMap.size() == 0) {
            // 当用户人数为0时重新开始游戏
            currentGame =  new Game(INFO);
        }
        if (userMap.size() == 4) {
            // 已满4个人
            LOGGER.info("房间人数已满！");
            // 向当前会话通知房间人数已满
            currentGame.setMessageType(INFO);
            currentGame.setMessage("房间人数已满");
            session.getBasicRemote().sendText(JSONObject.toJSONString(currentGame, SerializerFeature.DisableCircularReferenceDetect));

            // 广播通知某某用户进入房间
            currentGame.setMessage(name + "进入房间");
            sendMessage(currentGame);

            // 2s后关闭当前会话
            Thread.sleep(2000);
            session.close();
            return;
        }
        if (userMap.values().contains(name)) {
            // 防止用户多次准备
            LOGGER.info("该用户已存在. name={}", name);
            return;
        }

        // 添加用户会话信息
        userMap.put(this.session.getId(), name);
        webSocketSet.add(this);

        // 将用户加入牌桌
        List<User> userList = currentGame.getUserList();
        for (int i = 0; i < 4; i++) {
            User user = userList.get(i);
            if (user.getUserNickName() == null || name.equals(user.getUserNickName())) {
                user.setIndex(i);
                user.setUserNickName(name);
                break;
            }
        }
        // 广播用户进入房间
        currentGame.setMessageType(INFO);
        currentGame.setMessage(name + "进入房间");
        sendMessage(currentGame);
        LOGGER.info(userMap.toString());
    }

    @OnClose
    public void onClose(@PathParam("name") String name) throws Exception {
        // 用户下线前删除牌桌内的信息
        List<User> userList = currentGame.getUserList();
        for (int i = 0; i < 4; i++) {
            User user = userList.get(i);
            if (name.equals(user.getUserNickName())) {
                user.setUserNickName(null);
                user.setReady(false);
            }
        }

        // 广播用户下线
        currentGame.setMessageType(INFO);
        currentGame.setMessage(name + "退出房间");
        sendMessage(currentGame);

        // 删除用户会话信息
        userMap.remove(this.session.getId());
        webSocketSet.remove(this);
        LOGGER.info("{}下线", name);
    }

    @OnMessage
    public void onMessage(String str, Session session) throws Exception {
        LOGGER.info("用户{}，信息{}", userMap.get(this.session.getId()), str);
        Message receivedMessage = JSONObject.parseObject(str, new TypeReference<Message<String>>() {});
        String currentUserName = userMap.get(this.session.getId());

        if (receivedMessage.getType().equals(CHAT)) {
            sendMessage(receivedMessage);
            return;
        }

        if (receivedMessage.getType().equals(HEART_BEAT)) {
            // 心跳包不响应
            return;
        }
        // 响应准备操作
        if (receivedMessage.getType().equals(CLIENT_READY)) {
            List<User> userList = currentGame.getUserList();
            for (int i = 0; i < 4; i++) {
                User user = userList.get(i);
                if (currentUserName.equals(user.getUserNickName())) {
                    user.setReady(true);

                    // 广播通知某某用户已准备
                    currentGame.setMessageType(CLIENT_READY);
                    currentGame.setMessage(currentUserName);
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
                game = MajiangUtil.newGame(currentGame.getBankerName(), currentGame.getUserList());
            } else {
                game = MajiangUtil.newGame(currentUserName, currentGame.getUserList());
            }
            game.setMessageType(START_GAME);
            currentGame = game;

            // 游戏开始后设置全体用户状态为未准备，方便前端显示，等下次准备时再设为已准备
            currentGame.setUnReady();
            sendMessage(game);
            return;
        }

        // 响应游戏中的其它指令

        // 余牌列表
        List<Majiang> remainMajiangList = currentGame.getRemainMajiangList();
        // 当前玩家列表
        List<User> userList = currentGame.getUserList();
        // 当前轮到的玩家
        User currentUser = userList.stream().filter(user -> user.getUserNickName().equals(currentGame.getCurrentUserName())).collect(Collectors.toList()).get(0);
        // 按照座位顺序应该轮到的玩家
        User physicalNextUser;
        // 上一个出牌的玩家
        User lastUser = new User();
        // 吃、碰、杠、暗杠、胡才要用到的
        if (receivedMessage.getType() >= 6 && receivedMessage.getType() <= 11) {
            physicalNextUser = userList.stream().filter(user -> user.getUserNickName().equals(currentGame.getPhysicalNextUserName())).collect(Collectors.toList()).get(0);
            lastUser = userList.get((physicalNextUser.getIndex() + 4 - 1) % 4);
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

                    // 开金
                    Majiang jin = remainMajiangList.remove(0);
                    User banker = userList.stream().filter(user -> user.getUserNickName().equals(currentGame.getBankerName())).collect(Collectors.toList()).get(0);
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
                            // 依次判断是否能抢金，并确定抢金顺序
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
                String index = (String) receivedMessage.getMessage();
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
                // 此处不设置上一张出牌为空，有出牌时说明不在补花阶段
                // currentGame.setCurrentOutMajiang(null);
                sendMessage(currentGame);
                break;
            }
            case MJ_ADD_FLOWER: {
                // 判断是否和局
                if (remainMajiangList.size() <= 16) {
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
                String eatIds = (String) receivedMessage.getMessage();
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

                User pengUser = userList.stream().filter(user -> user.getUserNickName().equals(currentUserName)).collect(Collectors.toList()).get(0);

                pengUser.addMajiang(currentOutMajiang);
                pengUser.sortMajiangList();

                // 碰牌，三张牌为一样
                pengUser.setMajiangPeng(code);

                currentUser.sortMajiangList();

                // 更新游戏信息
                userList.set(currentUser.getIndex(), currentUser);
                userList.set(pengUser.getIndex(), pengUser);
                currentGame.setCurrentUserName(currentUserName);
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
                User gangUser = userList.stream().filter(user -> user.getUserNickName().equals(currentUserName)).collect(Collectors.toList()).get(0);
                gangUser.addMajiang(currentOutMajiang);
                gangUser.sortMajiangList();

                // 碰牌，三张牌为一样
                gangUser.setMajiangGang(code);

                currentUser.sortMajiangList();

                // 补花
                Majiang currentInMajiang = remainMajiangList.remove(remainMajiangList.size() - 1);
                currentUser.addMajiang(currentInMajiang);

                // 更新游戏信息
                currentGame.setRemainMajiangList(remainMajiangList);
                currentGame.setCurrentUserName(currentUserName);
                currentGame.setCurrentInMajiang(currentInMajiang);
                currentGame.setCurrentOutMajiang(null);
                userList.set(currentUser.getIndex(), currentUser);
                userList.set(gangUser.getIndex(), gangUser);
                currentGame.setNextUserNameList(new ArrayList<>());
                sendMessage(currentGame);
                break;
            }
            case MJ_AN_GANG: {
                String code = (String) receivedMessage.getMessage();

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
                userList.set(currentUser.getIndex(), currentUser);
                sendMessage(currentGame);
                break;
            }
            case MJ_JIA_GANG: {
                String code = (String) receivedMessage.getMessage();

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
                currentGame.setCurrentInMajiang(currentInMajiang);
                sendMessage(currentGame);
                break;
            }
            case MJ_HU: {
                String message = (String) receivedMessage.getMessage();
                Majiang currentOutMajiang = currentGame.getCurrentOutMajiang();
                User huUser = userList.stream().filter(user -> user.getUserNickName().equals(currentUserName)).collect(Collectors.toList()).get(0);
                // 庄家
                User banker = userList.stream().filter(user -> user.getUserNickName().equals(currentGame.getBankerName())).collect(Collectors.toList()).get(0);

                if ("抢金".equals(message)) {
                    // 判断是否抢金
                    List<Majiang> majiangList = huUser.getUserMajiangList();
                    if (majiangList.size() == 16 && MajiangUtil.canHuWithQiangJin(majiangList, currentGame.getJin())) {
                        huUser.addMajiang(currentGame.getJin());
                        huUser.sortMajiangList();

                        // 分数为20+2*(5+花+暗杠数+金)
                        int moneyNum = MajiangUtil.calculateScore(huUser, "抢金", currentGame);
                        huUser.setMajiangHu();

                        // 更新游戏信息
                        currentGame.setCurrentUserName(currentUserName);
                        // 设置下轮庄家名称
                        currentGame.setBankerName(currentGame.getBankerName().equals(huUser.getUserNickName()) ? huUser.getUserNickName() : userList.get((banker.getIndex() + 1) % 4).getUserNickName());
                        userList.set(huUser.getIndex(), huUser);
                        MajiangUtil.countScore(userList, huUser, moneyNum);
                        sendMessage(currentGame);
                    }
                }

                if (huUser.needAddMajiang()) {
                    // 用户的麻将数差1，还需要加一张才能胡
                    LOGGER.info("判断是否平胡");
                    if (currentOutMajiang == null) {
                        System.out.println("当前无出牌，胡牌操作无效");
                        return;
                    }
                    boolean canHu = MajiangUtil.canHuWithNewMajiang(huUser.getUserMajiangList(), currentOutMajiang);
                    System.out.println(canHu);
                    if (canHu) {
                        lastUser.removeLastOfOutList();
                        huUser.addMajiang(currentOutMajiang);
                        huUser.sortMajiangList();

                        // 分数为5+花+暗杠数+金
                        int moneyNum = MajiangUtil.calculateScore(huUser, "平胡", currentGame);
                        huUser.setMajiangHu();

                        // 更新游戏信息
                        userList.set(huUser.getIndex(), huUser);
                        MajiangUtil.countScore(userList, huUser, moneyNum);
                        currentGame.setBankerName(currentGame.getBankerName().equals(huUser.getUserNickName()) ? huUser.getUserNickName() : userList.get((banker.getIndex() + 1) % 4).getUserNickName());
                        currentGame.setCurrentOutMajiang(null);
                        currentGame.setCurrentUserName(currentUserName);
                        sendMessage(currentGame);
                    }
                } else {
                    LOGGER.info("判断是否自摸");
                    boolean canHu = MajiangUtil.canHu(huUser.getUserMajiangList(), true);
                    System.out.println(canHu);
                    if (canHu) {
                        huUser.sortMajiangList();

                        // 分数为2*（5+花+暗杠数+金+占庄数）
                        int moneyNum;
                        if (currentGame.getCurrentInMajiang() == null) {
                            // 如果是点了吃碰后再点的胡，则currentInMajiang为空，此处算分时应纠正为平胡
                            moneyNum = MajiangUtil.calculateScore(huUser, "平胡", currentGame);
                        } else {
                            moneyNum = MajiangUtil.calculateScore(huUser, "自摸", currentGame);
                        }
                        huUser.setMajiangHu();

                        // 更新游戏信息
                        userList.set(huUser.getIndex(), huUser);
                        MajiangUtil.countScore(userList, huUser, moneyNum);
                        currentGame.setBankerName(currentGame.getBankerName().equals(huUser.getUserNickName()) ? huUser.getUserNickName() : userList.get((banker.getIndex() + 1) % 4).getUserNickName());
                        currentGame.setCurrentUserName(currentUserName);
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
                // 游戏结束时设置金为空，方便前端判断游戏是否正在进行中，若金为空则需要前端准备
                currentGame.setJin(null);
                sendMessage(currentGame);
                break;
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error){
        LOGGER.error("websocket连接出错", error);
    }

    /**
     * 通过websocket发送游戏消息
     * @param game 当前游戏对象
     * @throws Exception
     */
    public void sendMessage(Game game) throws Exception {
        for (GameController gameController : webSocketSet) {
            if (gameController.session.isOpen()) {
                // SerializerFeature.DisableCircularReferenceDetect: 避免fastjson解析对象时出现循环引用$ref
                gameController.session.getBasicRemote().sendText(JSONObject.toJSONString(game, SerializerFeature.DisableCircularReferenceDetect));
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
            if (gameController.session.isOpen()) {
                // SerializerFeature.DisableCircularReferenceDetect: 避免fastjson解析对象时出现循环引用$ref
                gameController.session.getBasicRemote().sendText(JSONObject.toJSONString(message, SerializerFeature.DisableCircularReferenceDetect));
            }
        }
    }

    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void addOnlineCount() {
        GameController.onlineCount++;
    }

    public static synchronized void subOnlineCount() {
        GameController.onlineCount--;
    }
}
