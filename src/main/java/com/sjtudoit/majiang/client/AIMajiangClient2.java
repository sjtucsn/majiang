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
public class AIMajiangClient2 extends MajiangClient {
    private static Logger logger = LoggerFactory.getLogger(AIMajiangClient2.class);

    public AIMajiangClient2(String name) {
        super(name);
    }

    public AIMajiangClient2(String name, Integer tableId) {
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
        float currentScore = countScore(mjBottomCodeArray);
        int mjId1 = 0, mjId2 = 0;
        if (canEatMjCodeArray.contains(currentOutMjCode - 2) && canEatMjCodeArray.contains(currentOutMjCode - 1)) {
            List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode - 2));
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode - 1));
            float[] result = countMaxScoreWithOneOut(tmpMjList);
            float maxScore = result[1];
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
            float[] result = countMaxScoreWithOneOut(tmpMjList);
            float maxScore = result[1];
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
            float[] result = countMaxScoreWithOneOut(tmpMjList);
            float maxScore = result[1];
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
        float currentScore = countScore(mjBottomCodeArray);
        if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode).count() == 2) {
            List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode));
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode));
            float maxScore = countMaxScoreWithOneOut(tmpMjList)[1];
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
        float currentScore = countScore(mjBottomCodeArray);
        if (mjBottomCodeArray.stream().filter(code -> code == currentOutMjCode).count() == 3) {
            List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode));
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode));
            tmpMjList.remove(tmpMjList.indexOf(currentOutMjCode));
            float maxScore = countScore(tmpMjList);
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
        float currentScore = countScore(mjBottomCodeArray);
        for (int i = 0; i < mjBottomCodeArray.size() - 4; i++) {
            int code = mjBottomCodeArray.get(i);
            if (mjBottomCodeArray.get(i + 1).equals(code) && mjBottomCodeArray.get(i + 2).equals(code) && mjBottomCodeArray.get(i + 3).equals(code)) {
                List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
                tmpMjList.remove(i);
                tmpMjList.remove(i);
                tmpMjList.remove(i);
                tmpMjList.remove(i);
                float maxScore = countScore(tmpMjList);
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
        float currentScore = countScore(mjBottomCodeArray);
        for (int i = 0; i < mjBottomCodeArray.size(); i++) {
            int code = mjBottomCodeArray.get(i);
            if (mjOutArray.stream().filter(outCode -> outCode.equals(code)).count() == 3) {
                List<Integer> tmpMjList = new ArrayList<>(mjBottomCodeArray);
                tmpMjList.remove(i);
                float maxScore = countScore(tmpMjList);
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
        return (int) countMaxScoreWithOneOut(mjList)[0];
    }

    /**
     * 选出所有的顺子
     * @param mjList
     * @return
     */
    protected List<Integer> select3Chi(List<Integer> mjList) {
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
        }
        return tmpList;
    }

    /**
     * 选出所有和刻子
     * @param mjList
     * @return
     */
    protected List<Integer> select3Peng(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = 0; i < mjList.size(); i++) {
            if (i + 2 >= mjList.size()) {
                return tmpList;
            }
            int code1 = mjList.get(i);

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
     * 选出所有的112,122
     * @param mjList
     * @return
     */
    protected List<Integer> select3Good(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = 0; i < mjList.size(); i++) {
            if (i + 2 >= mjList.size()) {
                return tmpList;
            }
            int code1 = mjList.get(i);
            if (code1 % 10 > 8) {
                return tmpList;
            }
            int code2 = code1 + 1;

            int index1 = mjList.indexOf(code1);
            int index2 = mjList.indexOf(code2);

            if (index2 != -1 && mjList.get(index1 + 1) == code1 && (index2 + 1) < mjList.size() && mjList.get(index2 + 1) != code2) {
                // 1 1 2
                mjList.remove(index2);
                mjList.remove(index1);
                mjList.remove(index1);
                tmpList.add(code1);
                tmpList.add(code1);
                tmpList.add(code2);
                return tmpList;
            }

            if (index2 != -1 && (index2 + 1) < mjList.size() && mjList.get(index2 + 1) == code2 && mjList.get(index1 + 1) != code1) {
                // 1 2 2
                mjList.remove(index2);
                mjList.remove(index2);
                mjList.remove(index1);
                tmpList.add(code1);
                tmpList.add(code2);
                tmpList.add(code2);
                return tmpList;
            }
        }
        return tmpList;
    }

    /**
     * 选出所有的113,133
     * @param mjList
     * @return
     */
    protected List<Integer> select3Bad(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = 0; i < mjList.size(); i++) {
            if (i + 2 >= mjList.size()) {
                return tmpList;
            }
            int code1 = mjList.get(i);
            if (code1 % 10 > 7) {
                return tmpList;
            }
            int code3 = code1 + 2;

            int index1 = mjList.indexOf(code1);
            int index3 = mjList.indexOf(code3);

            if (index3 != -1 && mjList.get(index1 + 1) == code1 && (index3 + 1) < mjList.size() && mjList.get(index3 + 1) != code3) {
                // 1 1 3
                mjList.remove(index3);
                mjList.remove(index1);
                mjList.remove(index1);
                tmpList.add(code1);
                tmpList.add(code1);
                tmpList.add(code3);
                return tmpList;
            }

            if (index3 != -1 && (index3 + 1) < mjList.size() && mjList.get(index3 + 1) == code3 && mjList.get(index1 + 1) != code1) {
                // 1 3 3
                mjList.remove(index3);
                mjList.remove(index3);
                mjList.remove(index1);
                tmpList.add(code1);
                tmpList.add(code3);
                tmpList.add(code3);
                return tmpList;
            }
        }
        return tmpList;
    }

    /**
     * 选出雀
     * @param mjList
     * @return
     */
    protected List<Integer> select2Que(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = 0; i < mjList.size(); i++) {
            if (i + 1 >= mjList.size()) {
                return tmpList;
            }
            int code1 = mjList.get(i);

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
     * 选出2,3
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
        }
        return tmpList;
    }

    /**
     * 选出1,3
     * @param mjList
     * @return
     */
    protected List<Integer> select2BadType1(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = 0; i < mjList.size(); i++) {
            if (i + 1 >= mjList.size()) {
                return tmpList;
            }
            int code1 = mjList.get(i);
            int code2 = code1 + 1;
            int code3 = code2 + 1;

            int index1 = i;
            int index3 = mjList.indexOf(code3);

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

    /**
     * 选出1,2
     * @param mjList
     * @return
     */
    protected List<Integer> select2BadType2(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = 0; i < mjList.size(); i++) {
            if (i + 1 >= mjList.size()) {
                return tmpList;
            }
            int code1 = mjList.get(i);
            int code2 = code1 + 1;

            int index1 = i;
            int index2 = mjList.indexOf(code2);

            // 判断1,2和8,9
            if (index2 != -1) {
                mjList.remove(index1);
                mjList.remove(index2 - 1);
                tmpList.add(code1);
                tmpList.add(code2);
                return tmpList;
            }
        }
        return tmpList;
    }

    /**
     * 逆向选出所有的顺子和刻子
     * @param mjList
     * @return
     */
    private List<Integer> select3Reverse(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = mjList.size() - 1; i >= 2; i--) {
            // 判断顺子
            int code1 = mjList.get(i);
            int code2 = code1 - 1;
            int code3 = code2 - 1;

            int index1 = i;
            int index2 = mjList.indexOf(code2);
            int index3 = mjList.indexOf(code3);

            if (index2 != -1 && index3 != -1) {
                mjList.remove(index1);
                mjList.remove(index2);
                mjList.remove(index3);
                tmpList.add(code3);
                tmpList.add(code2);
                tmpList.add(code1);
                return tmpList;
            }

            // 判断刻子
            if (mjList.get(i).equals(mjList.get(i - 1)) && mjList.get(i - 1).equals(mjList.get(i - 2))) {
                mjList.remove(i - 2);
                mjList.remove(i - 2);
                mjList.remove(i - 2);
                tmpList.add(code1);
                tmpList.add(code1);
                tmpList.add(code1);
                return tmpList;
            }
        }
        return tmpList;
    }

    /**
     * 逆向选出雀和2,3
     * @param mjList
     * @return
     */
    private List<Integer> select2GoodReverse(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = mjList.size() - 1; i >= 1; i--) {
            int code1 = mjList.get(i);
            int code2 = code1 - 1;

            int index1 = i;
            int index2 = mjList.indexOf(code2);

            // 判断2,3
            if (index2 != -1 && code1 % 10 != 9 && code2 % 10 != 1) {
                mjList.remove(index1);
                mjList.remove(index2);
                tmpList.add(code2);
                tmpList.add(code1);
                return tmpList;
            }

            // 判断对子
            if (mjList.get(i).equals(mjList.get(i - 1))) {
                mjList.remove(i - 1);
                mjList.remove(i - 1);
                tmpList.add(code1);
                tmpList.add(code1);
                return tmpList;
            }
        }
        return tmpList;
    }

    /**
     * 逆向选出1,3和1,2
     * @param mjList
     * @return
     */
    protected List<Integer> select2BadReverse(List<Integer> mjList) {
        List<Integer> tmpList = new ArrayList<>();
        for (int i = mjList.size() - 1; i >= 1; i--) {
            int code1 = mjList.get(i);
            int code2 = code1 - 1;
            int code3 = code2 - 1;

            int index1 = i;
            int index2 = mjList.indexOf(code2);
            int index3 = mjList.indexOf(code3);

            // 判断1,2和8,9
            if (index2 != -1) {
                mjList.remove(index1);
                mjList.remove(index2);
                tmpList.add(code2);
                tmpList.add(code1);
                return tmpList;
            }

            // 判断1,3，防止9,10,11这样的出现
            if (index3 != -1 && code2 % 10 != 0) {
                mjList.remove(index1);
                mjList.remove(index3);
                tmpList.add(code3);
                tmpList.add(code1);
                return tmpList;
            }
        }
        return tmpList;
    }

    /**
     * 选择当前牌面需要打掉的牌
     * @param mjList 除anGang、show和jin的麻将code列表
     * @return outCode、maxScore
     */
    private float[] countMaxScoreWithOneOut(List<Integer> mjList) {
        System.out.println(mjList);
        float maxScore = 0;
        int outCode = mjList.get(0);
        for (int i = 0; i < mjList.size(); i++) {
            List<Integer> tmpMjList = new ArrayList<>(mjList);
            tmpMjList.remove(i);
            float score = countScore(tmpMjList);
            logger.info("打掉{}后的分数是{}", mjList.get(i), score);
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
        return new float[] {outCode, maxScore};
    }

    /**
     * 计算当前牌面的分数（离胡牌少一张牌时）
     * @param mjList 除anGang、show和jin的麻将code列表
     * @return 分数
     */
    private float countScore(List<Integer> mjList) {
        MajiangArrangeExecutor majiangArrangeExecutor = new MajiangArrangeExecutor(mjList);
        return majiangArrangeExecutor.countScore();
    }

    private class MajiangArrangeExecutor {
        private List<Integer> mjList;
        private List<Integer> select3List = new ArrayList<>();
        private List<Integer> select3GoodList = new ArrayList<>();
        private List<Integer> select3BadList = new ArrayList<>();
        private List<Integer> select2GoodList = new ArrayList<>();
        private List<Integer> select2QueList = new ArrayList<>();
        private List<Integer> select2BadList = new ArrayList<>();
        private List<Integer> select1List = new ArrayList<>();

        public MajiangArrangeExecutor(List<Integer> mjList) {
            this.mjList = mjList;
        }

        public float countScore() {
            arrangeMajiang();
            float score = (float) (select3List.size() / 3 * 30 +
                    select3GoodList.size() / 3 * 18 +
                    select3BadList.size() / 3 * 15 +
                    select2GoodList.size() / 2 * 12 +
                    (select2QueList.size() <= 2 ? select2QueList.size() / 2 * 12 : select2QueList.size() / 2 * 10) +
                    select2BadList.size() / 2 * 8 +
                    select1List.size() * 2);
            /*System.out.println("-----------------算分结果begin-----------------");
            System.out.println(select3List);
            System.out.println(select3GoodList);
            System.out.println(select3BadList);
            System.out.println(select2GoodList);
            System.out.println(select2QueList);
            System.out.println(select2BadList);
            System.out.println(select1List);
            System.out.println("-----------------算分结果end-----------------" + score);*/
            return score;
        }

        /**
         * 按照一种方式排列麻将
         */
        private void arrangeMajiang1() {
            List<Integer> mjList = new ArrayList<>(this.mjList);
            while (mjList.size() > 2) {
                List<Integer> listWith3 = select3(mjList);
                if (listWith3.isEmpty()) {
                    while (mjList.size() > 2) {
                        List<Integer> listWith3Good = select3Good(mjList);
                        if (listWith3Good.isEmpty()) {
                            while (mjList.size() > 2) {
                                List<Integer> listWith3Bad = select3Bad(mjList);
                                if (listWith3Bad.isEmpty()) {
                                    while (mjList.size() > 1) {
                                        List<Integer> listWith2Good = select2Good(mjList);
                                        if (listWith2Good.isEmpty()) {
                                            while (mjList.size() > 1) {
                                                List<Integer> listWith2Que = select2Que(mjList);
                                                if (listWith2Que.isEmpty()) {
                                                    while (mjList.size() > 1) {
                                                        List<Integer> listWith2BadType1 = select2BadType1(mjList);
                                                        if (listWith2BadType1.isEmpty()) {
                                                            while (mjList.size() > 1) {
                                                                List<Integer> listWith2BadType2 = select2BadType2(mjList);
                                                                if (listWith2BadType2.isEmpty()) {
                                                                    break;
                                                                } else {
                                                                    select2BadList.addAll(listWith2BadType2);
                                                                }
                                                            }
                                                            break;
                                                        } else {
                                                            select2BadList.addAll(listWith2BadType1);
                                                        }
                                                    }
                                                    break;
                                                } else {
                                                    select2QueList.addAll(listWith2Que);
                                                }
                                            }
                                            break;
                                        } else {
                                            select2GoodList.addAll(listWith2Good);
                                        }
                                    }
                                    break;
                                } else {
                                    select3BadList.addAll(listWith3Bad);
                                }
                            }
                            break;
                        } else {
                            select3GoodList.addAll(listWith3Good);
                        }
                    }
                    break;
                } else {
                    select3List.addAll(listWith3);
                }
            }
            select1List.addAll(mjList);
        }

        private void arrangeMajiang() {
            // 正向选择
            arrangeMajiang1();
            // 逆向选择
            /*List<Integer> mjListReverse = new ArrayList<>(this.mjList);
            while (mjListReverse.size() > 2) {
                List<Integer> listWith3Reverse = select3Reverse(mjListReverse);
                if (listWith3Reverse.isEmpty()) {
                    while (mjListReverse.size() > 1) {
                        List<Integer> listWith2GoodReverse = select2GoodReverse(mjListReverse);
                        if (listWith2GoodReverse.isEmpty()) {
                            while (mjListReverse.size() > 1) {
                                List<Integer> listWith2BadReverse = select2BadReverse(mjListReverse);
                                if (listWith2BadReverse.isEmpty()) {
                                    break;
                                } else {
                                    select2BadList.addAll(listWith2BadReverse);
                                }
                            }
                            break;
                        } else {
                            select2GoodList.addAll(listWith2GoodReverse);
                        }
                    }
                    break;
                } else {
                    select3GoodList.addAll(listWith3Reverse);
                }
            }
            select1List.addAll(mjListReverse);*/
        }
    }
}
