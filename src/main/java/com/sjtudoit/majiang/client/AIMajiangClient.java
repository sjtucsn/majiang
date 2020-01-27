package com.sjtudoit.majiang.client;

import com.sjtudoit.majiang.constant.MessageType;
import com.sjtudoit.majiang.dto.Majiang;
import com.sjtudoit.majiang.dto.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.ClientEndpoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ClientEndpoint
public class AIMajiangClient extends MajiangClient {
    private static Logger logger = LoggerFactory.getLogger(AIMajiangClient.class);

    public AIMajiangClient(String name) {
        super(name);
    }

    public AIMajiangClient(String name, Integer tableId) {
        super(name, tableId);
    }

    @Override
    protected boolean handleChi() {
        if (game.getCurrentOutMajiang() == null) {
            return false;
        }
        int currentOutMjCode = game.getCurrentOutMajiang().getCode();
        List<Majiang> mjBottomArray = user.getUserMajiangList().stream().filter(majiang -> !majiang.isJin() && !majiang.isAnGang() && !majiang.isShow()).collect(Collectors.toList());
        List<Integer> canEatMjCodeArray = mjBottomArray.stream().map(Majiang::getCode).filter(code -> code - currentOutMjCode <= 2 || code - currentOutMjCode >= 2).collect(Collectors.toList());
        List<Integer> mjBottomCodeArray = mjBottomArray.stream().map(Majiang::getCode).collect(Collectors.toList());
        int currentScore = countScore(mjBottomCodeArray);
        int mjId1 = 0, mjId2 = 0;
        if (canEatMjCodeArray.contains(currentOutMjCode - 2) && canEatMjCodeArray.contains(currentOutMjCode - 1)) {
            List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode - 2));
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode - 1));
            int[] result = countMaxScoreWithOneOut(tmpMjList);
            int maxScore = result[1];
            logger.info("当前分数{}，如果按照这么吃{}后的分数是{}", currentScore, (currentOutMjCode - 2) + " " + (currentOutMjCode - 1) + " " + currentOutMjCode, maxScore + 30);
            if (maxScore + 30 > currentScore && result[0] != currentOutMjCode && !((currentOutMjCode % 10 > 3 ) && result[0] == currentOutMjCode - 3)) {
                mjId1 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode - 2).findFirst().get().getId();
                mjId2 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode - 1).findFirst().get().getId();
            }
        }
        if (canEatMjCodeArray.contains(currentOutMjCode - 1) && canEatMjCodeArray.contains(currentOutMjCode + 1)) {
            List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode - 1));
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode + 1));
            int[] result = countMaxScoreWithOneOut(tmpMjList);
            int maxScore = result[1];
            logger.info("当前分数{}，如果按照这么吃{}后的分数是{}", currentScore, (currentOutMjCode - 1) + " " + currentOutMjCode + " " + (currentOutMjCode + 1), maxScore + 30);
            if (maxScore + 30 > currentScore && result[0] != currentOutMjCode) {
                mjId1 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode - 1).findFirst().get().getId();
                mjId2 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode + 1).findFirst().get().getId();
            }
        }
        if (canEatMjCodeArray.contains(currentOutMjCode + 1) && canEatMjCodeArray.contains(currentOutMjCode + 2)) {
            List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode + 1));
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode + 2));
            int[] result = countMaxScoreWithOneOut(tmpMjList);
            int maxScore = result[1];
            logger.info("当前分数{}，如果按照这么吃{}后的分数是{}", currentScore, currentOutMjCode + " " + (currentOutMjCode + 1) + " " + (currentOutMjCode + 2), maxScore + 30);
            if (maxScore + 30 > currentScore && result[0] != currentOutMjCode && !((currentOutMjCode % 10 < 7) && result[0] == currentOutMjCode + 3)) {
                mjId1 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode + 1).findFirst().get().getId();
                mjId2 = mjBottomArray.stream().filter(majiang -> majiang.getCode() == currentOutMjCode + 2).findFirst().get().getId();
            }
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

    @Override
    protected boolean handlePeng() {
        if (game.getCurrentOutMajiang() == null) {
            return false;
        }
        int currentOutMjCode = game.getCurrentOutMajiang().getCode();
        List<Integer> mjBottomCodeArray = user.getUserMajiangList().stream().filter(majiang -> !majiang.isJin() && !majiang.isAnGang() && !majiang.isShow()).map(Majiang::getCode).collect(Collectors.toList());
        int currentScore = countScore(mjBottomCodeArray);
        if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode).count() == 2) {
            List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode));
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode));
            int maxScore = countMaxScoreWithOneOut(tmpMjList)[1];
            logger.info("当前分数{}，如果按照这么碰{}后的分数是{}", currentScore, currentOutMjCode + " " + currentOutMjCode + " " + currentOutMjCode, maxScore + 30);
            if (maxScore + 30 > currentScore) {
                send(new Message(MessageType.MJ_PENG, "碰"));
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    protected boolean handleGang() {
        if (game.getCurrentOutMajiang() == null) {
            return false;
        }
        int currentOutMjCode = game.getCurrentOutMajiang().getCode();
        List<Integer> mjBottomCodeArray = user.getUserMajiangList().stream().filter(majiang -> !majiang.isJin() && !majiang.isAnGang() && !majiang.isShow()).map(Majiang::getCode).collect(Collectors.toList());
        int currentScore = countScore(mjBottomCodeArray);
        if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode).count() == 3) {
            List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode));
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode));
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode));
            int maxScore = countScore(tmpMjList);
            // 开杠，分数加多6分
            logger.info("当前分数{}，如果按照这么杠{}后的分数是{}", currentScore, currentOutMjCode + " " + currentOutMjCode + " " + currentOutMjCode + " " + currentOutMjCode, maxScore + 36);
            if (maxScore + 36 > currentScore) {
                send(new Message(MessageType.MJ_GANG, "杠"));
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    protected int handleAnGang() {
        List<Integer> mjBottomCodeArray = user.getUserMajiangList().stream().filter(majiang -> !majiang.isJin() && !majiang.isAnGang() && !majiang.isShow()).map(Majiang::getCode).sorted(Integer::compareTo).collect(Collectors.toList());
        if (mjBottomCodeArray.size() < 4) {
            return -1;
        }
        int currentScore = countScore(mjBottomCodeArray);
        for (int i = 0; i < mjBottomCodeArray.size() - 4; i++) {
            int code = mjBottomCodeArray.get(i);
            if (mjBottomCodeArray.get(i + 1).equals(code) && mjBottomCodeArray.get(i + 2).equals(code) && mjBottomCodeArray.get(i + 3).equals(code)) {
                List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
                tmpMjList.remove(i);
                tmpMjList.remove(i);
                tmpMjList.remove(i);
                tmpMjList.remove(i);
                int maxScore = countScore(tmpMjList);
                // 暗杠，分数加多10分
                logger.info("当前分数{}，如果按照这么暗杠{}后的分数是{}", currentScore, code + " " + code + " " + code + " " + code, maxScore + 40);
                if (maxScore + 40 > currentScore) {
                    return code;
                } else {
                    return -1;
                }
            }
        }
        return -1;
    }

    @Override
    protected int handleJiaGang() {
        List<Integer> mjBottomCodeArray = user.getUserMajiangList().stream().filter(majiang -> !majiang.isJin() && !majiang.isAnGang() && !majiang.isShow()).map(Majiang::getCode).sorted(Integer::compareTo).collect(Collectors.toList());
        List<Integer> mjOutArray = user.getUserMajiangList().stream().filter(Majiang::isShow).map(Majiang::getCode).collect(Collectors.toList());
        if (mjOutArray.size() < 3) {
            return -1;
        }
        int currentScore = countScore(mjBottomCodeArray);
        for (int i = 0; i < mjBottomCodeArray.size(); i++) {
            int code = mjBottomCodeArray.get(i);
            if (mjOutArray.stream().filter(outCode -> outCode.equals(code)).count() == 3) {
                List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
                tmpMjList.remove(i);
                int maxScore = countScore(tmpMjList);
                // 加杠，分数加多6分，但之前那30是已经碰的牌，此处不加
                logger.info("当前分数{}，如果按照这么加杠{}后的分数是{}", currentScore, code + " " + code + " " + code + " " + code, maxScore + 6);
                if (maxScore + 6 > currentScore) {
                    return code;
                } else {
                    return -1;
                }
            }
        }
        return -1;
    }

    @Override
    protected int selectOutMajiang(List<Majiang> userMajiangList) {
        List<Integer> mjList = userMajiangList.stream().filter(majiang -> !majiang.isAnGang() && !majiang.isShow() && !majiang.isJin()).map(Majiang::getCode).sorted(Integer::compareTo).collect(Collectors.toList());
        return countMaxScoreWithOneOut(mjList)[0];
    }

    @Override
    protected List<Integer> select3(List<Integer> mjList) {
        return super.select3(mjList);
    }

    @Override
    protected List<Integer> select2Good(List<Integer> mjList) {
        return super.select2Good(mjList);
    }

    @Override
    protected List<Integer> select2Bad(List<Integer> mjList) {
        return super.select2Bad(mjList);
    }

    /**
     * 选择当前牌面需要打掉的牌
     * @param mjList 除anGang、show和jin的麻将code列表
     * @return outCode、maxScore
     */
    private int[] countMaxScoreWithOneOut(List<Integer> mjList) {
        System.out.println(mjList);
        int maxScore = 0;
        int outCode = mjList.get(0);
        for (int i = 0; i < mjList.size(); i++) {
            List<Integer> tmpMjList = new ArrayList<>(mjList);
            tmpMjList.remove(i);
            int score = countScore(tmpMjList);
            // logger.info("打掉{}后的分数是{}", mjList.get(i), score);
            if (score >= maxScore) {
                if (score == maxScore) {
                    // 分数相等时选择是否替换，需保持每次结果一致
                    if (outCode % 2 == session.getId().charAt(0) % 2) {
                        outCode = mjList.get(i);
                    }
                } else {
                    maxScore = score;
                    outCode = mjList.get(i);
                }
            }
        }
        return new int[] {outCode, maxScore};
    }

    /**
     * 计算当前牌面的分数（离胡牌少一张牌时）
     * @param mjList 除anGang、show和jin的麻将code列表
     * @return 分数
     */
    private int countScore(List<Integer> mjList) {
        MajiangArrangeExecutor majiangArrangeExecutor = new MajiangArrangeExecutor(mjList);
        return majiangArrangeExecutor.countScore();
    }

    private class MajiangArrangeExecutor {
        private List<Integer> mjList;
        private List<Integer> select3List = new ArrayList<>();
        private List<Integer> select2GoodList = new ArrayList<>();
        private List<Integer> select2BadList = new ArrayList<>();
        private List<Integer> select1List = new ArrayList<>();

        public MajiangArrangeExecutor(List<Integer> mjList) {
            this.mjList = mjList;
        }

        public int countScore() {
            arrangeMajiang();
            /* System.out.println("-----------------算分结果begin-----------------");
            System.out.println(select3List);
            System.out.println(select2GoodList);
            System.out.println(select2BadList);
            System.out.println(select1List);
            System.out.println("-----------------算分结果end-----------------" + (select3List.size() * 10 + select2GoodList.size() * 8 + select2BadList.size() * 6 + select1List.size() * 5)); */
            return select3List.size() / 3 * 30 + select2GoodList.size() / 2 * 16 + select2BadList.size() / 2 * 12 + select1List.size() * 5;
        }

        private void arrangeMajiang() {
            List<Integer> mjList = new ArrayList<>(this.mjList);
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
                                    select2BadList.addAll(listWith2Bad);
                                }
                            }
                            break;
                        } else {
                            select2GoodList.addAll(listWith2Good);
                        }
                    }
                    break;
                } else {
                    select3List.addAll(listWith3);
                }
            }
            select1List.addAll(mjList);
        }
    }
}
