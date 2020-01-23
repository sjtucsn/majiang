package com.sjtudoit.majiang.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.sjtudoit.majiang.constant.MessageType;
import com.sjtudoit.majiang.dto.Game;
import com.sjtudoit.majiang.dto.Majiang;
import com.sjtudoit.majiang.dto.Message;
import com.sjtudoit.majiang.dto.User;
import com.sjtudoit.majiang.utils.MajiangUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import java.util.*;
import java.util.stream.Collectors;

@ClientEndpoint
public class MajiangClient {
    private static Logger logger = LoggerFactory.getLogger(MajiangClient.class);
    protected Session session;
    protected String name;
    protected User user;
    protected Game game;
    private boolean ready = false;

    public MajiangClient() {
    }

    public MajiangClient(String name) {
        this.name = name;
    }

    @OnOpen
    public void open(Session session) {
        logger.info(this.name + "机器人连接建立");
        // 设置websocket传输text数据的缓冲大小，否则传数据时连接可能异常关闭（一条game数据大约为10000字节）
        session.setMaxTextMessageBufferSize(15000);
        this.session = session;
    }

    @OnMessage
    public void onMessage(String message) throws Exception {
        game = JSON.parseObject(message, Game.class);
        if (game.getMessageType() == null) {
            return;
        }
        if (game.getMessageType().equals(MessageType.INFO) && game.getMessage().equals(this.name + "进入房间")) {
            // 电脑进入房间后自动准备
            send(new Message(MessageType.CLIENT_READY, "准备"));
            return;
        }
        if (game.getMessageType() <= MessageType.CLIENT_READY) {
            // 准备以及之前的消息类型无需响应
            return;
        }
        if (game.getMessageType() >= MessageType.HU_PING_HU && game.getMessageType() <= MessageType.MJ_TIE) {
            // 本局游戏结束
            ready = false;
            Thread.sleep(200 + new Random().nextInt(300));
            send(new Message(MessageType.GAME_OVER));
            return;
        }

        game.getUserList().stream().filter(user1 -> user1.getUserNickName() != null && user1.getUserNickName().equals(this.name)).findFirst().ifPresent(value -> user = value);
        String currentUserName = game.getCurrentUserName();
        Majiang currentOutMajiang = game.getCurrentOutMajiang();
        Majiang currentInMajiang = game.getCurrentInMajiang();

        if (user == null || currentUserName == null) {
            // 若用户信息还不存在也无需响应
            return;
        }

        if (game.getMessageType().equals(MessageType.GAME_OVER) && !ready) {
            // 一局游戏结束后自动准备
            Thread.sleep(200 + new Random().nextInt(300));
            ready = true;
            send(new Message(MessageType.CLIENT_READY, "准备"));
            return;
        }

        if (user.getCanQiangJin()) {
            if (user.getBanker()) {
                // 庄家抢金，先判断之前打的牌对不对，是否现在可以抢金
                if (MajiangUtil.canHuWithNewMajiang(user.getUserMajiangList(), game.getJin())) {
                    send(new Message(MessageType.MJ_HU, "抢金"));
                }
            } else {
                // 非庄家自动抢金判断
                send(new Message(MessageType.MJ_HU, "抢金"));
            }
            return;
        }

        // 轮到自己时
        Thread.sleep(1000);
        if (currentUserName.equals(this.name)) {
            // 开始阶段补花
            if (currentOutMajiang == null && currentInMajiang.getId() == null) {
                if (user.getUserMajiangList().stream().anyMatch(majiang -> majiang.getCode() > 30)) {
                    send(new Message(MessageType.RESET_FLOWER, "开始阶段补花"));
                    return;
                }
            }

            // 庄家抢金判断
            if (user.getCanQiangJin() && user.getBanker()) {
                for (int j = 0; j < user.getUserMajiangList().size(); j++) {
                    List<Majiang> majiangList = new ArrayList<>(user.getUserMajiangList());
                    majiangList.remove(j);
                    if (MajiangUtil.canHuWithQiangJin(majiangList, game.getJin())) {
                        // 打掉这张牌，则可以抢金
                        send(new Message(MessageType.MJ_OUT, String.valueOf(j)));
                        return;
                    }
                }
            }

            // 游戏过程中遇到非上家打的碰、杠、胡情况
            if (!currentUserName.equals(game.getPhysicalNextUserName())) {
                if (user.getUserMajiangList().stream().filter(majiang -> !majiang.isAnGang() && !majiang.isShow()).count() % 3 == 1) {
                    // 遇到碰杠胡等情况
                    if (MajiangUtil.canHuWithNewMajiang(user.getUserMajiangList(), currentOutMajiang)) {
                        send(new Message(MessageType.MJ_HU, "胡"));
                    } else if (handleGang()) {
                        logger.info("杠了");
                    } else if (handlePeng()) {
                        logger.info("碰了");
                    } else {
                        send(new Message(MessageType.PASS, "过"));
                    }
                    return;
                }
            }

            // 正常顺序轮到自己时
            int showNum = (int) user.getUserMajiangList().stream().filter(majiang -> majiang.isAnGang() || majiang.isShow()).count();
            if (user.getUserMajiangList().stream().filter(majiang -> !majiang.isShow() && !majiang.isAnGang()).count() % 3 == 2) {
                if (currentInMajiang.getCode() != null && currentInMajiang.getCode() > 30) {
                    // 摸到的是花
                    send(new Message(MessageType.MJ_ADD_FLOWER, "补花"));
                    return;
                } else {
                    // 摸到的不是花，判断是不是自摸，再判断是否需要加杠与暗杠，如果不是则根据算法打掉一张牌
                    if (MajiangUtil.canHu(user.getUserMajiangList(), true)) {
                        send(new Message(MessageType.MJ_HU, "胡"));
                        return;
                    }
                    // 判断是否要暗杠
                    int anGangCode = handleAnGang();
                    if (anGangCode != -1) {
                        send(new Message(MessageType.MJ_AN_GANG, String.valueOf(anGangCode)));
                        return;
                    }
                    // 判断是否要加杠
                    int jiaGangCode = handleJiaGang();
                    if (jiaGangCode != -1) {
                        send(new Message(MessageType.MJ_JIA_GANG, String.valueOf(jiaGangCode)));
                        return;
                    }
                    // 不胡、不暗杠且不加杠，则选择需要打的牌
                    int outCode = selectOutMajiang(user.getUserMajiangList());
                    int index = user.getUserMajiangList().stream().filter(majiang -> !majiang.isAnGang() && !majiang.isShow()).map(Majiang::getCode).collect(Collectors.toList()).indexOf(outCode) + showNum;
                    send(new Message(MessageType.MJ_OUT, String.valueOf(index)));
                    return;
                }
            } else {
                // 判断上家打的牌能否胡杠碰吃，否则抓牌
                if (MajiangUtil.canHuWithNewMajiang(user.getUserMajiangList(), currentOutMajiang)) {
                    send(new Message(MessageType.MJ_HU, "胡"));
                    logger.info("平胡");
                } else if (handleGang()) {
                    logger.info("杠了");
                } else if (handlePeng()) {
                    logger.info("碰了");
                } else if (handleChi()) {
                    logger.info("吃了");
                } else {
                    send(new Message(MessageType.MJ_IN, "抓牌"));
                }
                return;
            }
        }
    }

    @OnClose
    public void onClose() throws Exception{
        logger.info(name + "连接关闭，id是" + session.getId());
    }

    /**
     * 发送客户端消息到服务端
     * @param message 消息内容
     */
    public void send(Message message) {
        try {
            if (this.session.isOpen()) {
                this.session.getBasicRemote().sendText(JSONObject.toJSONString(message, SerializerFeature.DisableCircularReferenceDetect));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Session getSession() {
        return session;
    }

    public String getName() {
        return name;
    }

    /**
     * 处理吃牌逻辑
     * @return 是否吃
     */
    protected boolean handleChi() {
        if (game.getCurrentOutMajiang() == null) {
            return false;
        }
        int currentOutMjCode = game.getCurrentOutMajiang().getCode();
        List<Majiang> mjBottomArray = user.getUserMajiangList().stream().filter(majiang -> !majiang.isJin() && !majiang.isAnGang() && !majiang.isShow()).collect(Collectors.toList());
        // 对于任意一张麻将code，判断code-3,code和code+3是否存在，如果存在就不吃
        List<Integer> canEatMjCodeArray = mjBottomArray.stream().map(Majiang::getCode).filter(code -> code - currentOutMjCode <= 2 || code - currentOutMjCode >= 2).collect(Collectors.toList());
        List<Integer> mjBottomCodeArray = mjBottomArray.stream().map(Majiang::getCode).collect(Collectors.toList());
        int mjId1 = 0, mjId2 = 0;
        if (canEatMjCodeArray.contains(currentOutMjCode - 2) && canEatMjCodeArray.contains(currentOutMjCode - 1) &&
                !(mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode - 3).count() == 1) &&
                !(mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode - 3).count() == 2) &&
                !(mjBottomCodeArray.stream().filter(code -> code.equals(currentOutMjCode)).count() == 1)) {
            mjId1 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode - 2).findFirst().get().getId();
            mjId2 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode - 1).findFirst().get().getId();
        } else if (canEatMjCodeArray.contains(currentOutMjCode - 1) && canEatMjCodeArray.contains(currentOutMjCode + 1) &&
                !(mjBottomCodeArray.stream().filter(code -> code.equals(currentOutMjCode)).count() == 1)) {
            mjId1 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode - 1).findFirst().get().getId();
            mjId2 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode + 1).findFirst().get().getId();
        } else if (canEatMjCodeArray.contains(currentOutMjCode + 1) && canEatMjCodeArray.contains(currentOutMjCode + 2) &&
                !(mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode + 3).count() == 1) &&
                !(mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode + 3).count() == 2) &&
                !(mjBottomCodeArray.stream().filter(code -> code.equals(currentOutMjCode)).count() == 1)) {
            mjId1 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode + 1).findFirst().get().getId();
            mjId2 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode + 2).findFirst().get().getId();
        }
        if (mjId1 != 0 && mjId2 != 0) {
            int mjId = game.getCurrentOutMajiang().getId();
            int[] arr = new int[] {mjId, mjId1, mjId2};
            Arrays.sort(arr);
            send(new Message(MessageType.MJ_CHI, arr[0] + " " + arr[1] + " " + arr[2]));
            return true;
        } else {
            return false;
        }
    }

    /**
     * 处理碰牌逻辑
     * @return 是否碰
     */
    protected boolean handlePeng() {
        if (game.getCurrentOutMajiang() == null) {
            return false;
        }
        int currentOutMjCode = game.getCurrentOutMajiang().getCode();
        List<Integer> mjBottomCodeArray = user.getUserMajiangList().stream().filter(majiang -> !majiang.isJin() && !majiang.isAnGang() && !majiang.isShow()).map(Majiang::getCode).collect(Collectors.toList());
        System.out.println(mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode).count());
        if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode).count() == 2) {
            if (currentOutMjCode % 10 == 3) {
                if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode - 1).count() == 1 &&
                    mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode - 2).count() == 1) {
                    return false;
                }
            } else if (currentOutMjCode % 10 == 7) {
                if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode + 1).count() == 1 &&
                        mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode + 2).count() == 1) {
                    return false;
                }
            } else if (currentOutMjCode % 10 != 1 && currentOutMjCode % 10 != 9) {
                if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode - 1).count() == 1 &&
                        mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode + 1).count() == 1) {
                    return false;
                }
            }
            send(new Message(MessageType.MJ_PENG, "碰"));
            return true;
        } else {
            return false;
        }
    }

    /**
     * 处理杠牌代码
     * @return 是否杠
     */
    protected boolean handleGang() {
        if (game.getCurrentOutMajiang() == null) {
            return false;
        }
        int currentOutMjCode = game.getCurrentOutMajiang().getCode();
        List<Integer> mjBottomCodeArray = user.getUserMajiangList().stream().filter(majiang -> !majiang.isJin() && !majiang.isAnGang() && !majiang.isShow()).map(Majiang::getCode).collect(Collectors.toList());
        if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode).count() == 3) {
            if (currentOutMjCode % 10 == 3) {
                if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode - 1).count() == 1 &&
                        mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode - 2).count() == 1) {
                    return false;
                }
            } else if (currentOutMjCode % 10 == 7) {
                if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode + 1).count() == 1 &&
                        mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode + 2).count() == 1) {
                    return false;
                }
            } else if (currentOutMjCode % 10 != 1 && currentOutMjCode % 10 != 9) {
                if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode - 1).count() == 1 &&
                        mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode + 1).count() == 1) {
                    return false;
                }
            }
            send(new Message(MessageType.MJ_GANG, "杠"));
            return true;
        } else {
            return false;
        }
    }

    /**
     * 判断是否暗杠
     * @return 返回将要暗杠的牌的编号，若不暗杠则返回-1
     */
    protected int handleAnGang() {
        List<Integer> mjBottomCodeArray = user.getUserMajiangList().stream().filter(majiang -> !majiang.isJin() && !majiang.isAnGang() && !majiang.isShow()).map(Majiang::getCode).sorted(Integer::compareTo).collect(Collectors.toList());
        if (mjBottomCodeArray.size() < 4) {
            return -1;
        }
        for (int i = 0; i < mjBottomCodeArray.size() - 4; i++) {
            int code = mjBottomCodeArray.get(i);
            if (mjBottomCodeArray.get(i + 1).equals(code) && mjBottomCodeArray.get(i + 2).equals(code) && mjBottomCodeArray.get(i + 3).equals(code)) {
                if (!mjBottomCodeArray.contains(code - 1) && !mjBottomCodeArray.contains(code + 1)) {
                    return code;
                }
            }
        }
        return -1;
    }

    /**
     * 判断是否加杠
     * @return 返回将要加杠的牌的编号，若不加杠则返回-1
     */
    protected int handleJiaGang() {
        List<Integer> mjBottomCodeArray = user.getUserMajiangList().stream().filter(majiang -> !majiang.isJin() && !majiang.isAnGang() && !majiang.isShow()).map(Majiang::getCode).sorted(Integer::compareTo).collect(Collectors.toList());
        List<Integer> mjOutArray = user.getUserMajiangList().stream().filter(Majiang::isShow).map(Majiang::getCode).collect(Collectors.toList());
        if (mjOutArray.size() < 3) {
            return -1;
        }
        for (int i = 0; i < mjBottomCodeArray.size(); i++) {
            int code = mjBottomCodeArray.get(i);
            if (mjOutArray.stream().filter(outCode -> outCode.equals(code)).count() == 3) {
                if (!mjBottomCodeArray.contains(code - 1) && !mjBottomCodeArray.contains(code + 1)) {
                    return code;
                }
            }
        }
        return -1;
    }

    /**
     * 选择要打的麻将编号
     * @param userMajiangList 玩家麻将列表
     * @return 需要打出的麻将编号
     */
    protected int selectOutMajiang(List<Majiang> userMajiangList) {
        List<Integer> mjList = userMajiangList.stream().filter(majiang -> !majiang.isAnGang() && !majiang.isShow() && !majiang.isJin()).map(Majiang::getCode).sorted(Integer::compareTo).collect(Collectors.toList());
        List<Integer> list3 = new ArrayList<>();
        List<Integer> list2Good = new ArrayList<>();
        List<Integer> list2Bad = new ArrayList<>();
        while (mjList.size() > 2) {
            List<Integer> listWith3 = select3(mjList);
            if (listWith3.isEmpty()) {
                while (mjList.size() > 1) {
                    List<Integer> listWith2Good = select2Good(mjList);
                    if (listWith2Good.isEmpty()) {
                        while (mjList.size() > 1) {
                            List<Integer> listWith2Bad = select2Bad(mjList);
                            if (listWith2Bad.isEmpty()) {
                                break;
                            } else {
                                list2Bad.addAll(listWith2Bad);
                            }
                        }
                        break;
                    } else {
                        list2Good.addAll(listWith2Good);
                    }
                }
                break;
            } else {
                list3.addAll(listWith3);
            }
        }
        List<Integer> list1 = new ArrayList<>(mjList);
        if (!list1.isEmpty()) {
            return list1.get(new Random().nextInt(list1.size()));
        } else if (!list2Bad.isEmpty()) {
            return list2Bad.get(new Random().nextInt(list2Bad.size()));
        } else if (!list2Good.isEmpty()) {
            // 当雀和2,3同时存在时
            return list2Good.get(new Random().nextInt(list2Good.size()));
        } else {
            // 应该不会运行到这一步（若是如此直接就胡了）
            return list3.get(0);
        }
    }

    /**
     * 选出所有的顺子和刻子
     * @param mjList
     * @return
     */
    protected List<Integer> select3(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = 0; i < mjList.size(); i++) {
            if (i + 2 >= mjList.size()) {
                return tmpList;
            }
            // 判断顺子
            int code1 = mjList.get(i);
            int code2 = code1 + 1;
            int code3 = code2 + 1;

            int index1 = i;
            int index2 = mjList.indexOf(code2);
            int index3 = mjList.indexOf(code3);

            if (index2 != -1 && index3 != -1) {
                mjList.remove(index1);
                mjList.remove(index2 - 1);
                mjList.remove(index3 - 2);
                tmpList.add(code1);
                tmpList.add(code2);
                tmpList.add(code3);
                return tmpList;
            }

            // 判断刻子
            if (mjList.get(i).equals(mjList.get(i + 1)) && mjList.get(i + 1).equals(mjList.get(i + 2))) {
                mjList.remove(i);
                mjList.remove(i);
                mjList.remove(i);
                tmpList.add(code1);
                tmpList.add(code1);
                tmpList.add(code1);
                return tmpList;
            }
        }
        return tmpList;
    }

    /**
     * 选出雀和2,3
     * @param mjList
     * @return
     */
    protected List<Integer> select2Good(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = 0; i < mjList.size(); i++) {
            if (i + 1 >= mjList.size()) {
                return tmpList;
            }
            int code1 = mjList.get(i);
            int code2 = code1 + 1;

            int index1 = i;
            int index2 = mjList.indexOf(code2);

            // 判断2,3
            if (index2 != -1 && code1 % 10 != 1 && code2 % 10 != 9) {
                mjList.remove(index1);
                mjList.remove(index2 - 1);
                tmpList.add(code1);
                tmpList.add(code2);
                return tmpList;
            }

            // 判断对子
            if (mjList.get(i).equals(mjList.get(i + 1))) {
                mjList.remove(i);
                mjList.remove(i);
                tmpList.add(code1);
                tmpList.add(code1);
                return tmpList;
            }
        }
        return tmpList;
    }

    /**
     * 选出1,3和1,2
     * @param mjList
     * @return
     */
    protected List<Integer> select2Bad(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = 0; i < mjList.size(); i++) {
            if (i + 1 >= mjList.size()) {
                return tmpList;
            }
            int code1 = mjList.get(i);
            int code2 = code1 + 1;
            int code3 = code2 + 1;

            int index1 = i;
            int index2 = mjList.indexOf(code2);
            int index3 = mjList.indexOf(code3);

            // 判断1,2和8,9
            if (index2 != -1) {
                mjList.remove(index1);
                mjList.remove(index2 - 1);
                tmpList.add(code1);
                tmpList.add(code2);
                return tmpList;
            }

            // 判断1,3，防止9,10,11这样的出现
            if (index3 != -1 && code2 % 10 != 0) {
                mjList.remove(index1);
                mjList.remove(index3 - 1);
                tmpList.add(code1);
                tmpList.add(code3);
                return tmpList;
            }
        }
        return tmpList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MajiangClient that = (MajiangClient) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "MajiangClient{" +
                "session=" + (session == null ? -1 : session.getId()) + " " + name +
                '}';
    }
}
