package com.sjtudoit.majiang.utils;

import com.sjtudoit.majiang.dto.Game;
import com.sjtudoit.majiang.dto.Majiang;
import com.sjtudoit.majiang.dto.User;

import java.util.*;
import java.util.stream.Collectors;

import static com.sjtudoit.majiang.constant.MessageType.*;

public class MajiangUtil {
    private static List<Integer> mjCodeArray = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22, 23, 24, 25, 26, 27, 28, 29);

    public static List<Majiang> generateMajiangList() {
        Integer[] codeArray = new Integer[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22, 23, 24, 25, 26, 27, 28, 29, 31, 32, 33, 34, 35};
        String [] nameArray = new String[] {
                "一万", "二万", "三万", "四万", "五万", "六万", "七万", "八万", "九万",
                "一筒", "二筒", "三筒", "四筒", "五筒", "六筒", "七筒", "八筒", "九筒",
                "一条", "二条", "三条", "四条", "五条", "六条", "七条", "八条", "九条",
                "东", "西", "南", "北", "中"
        };

        List<Integer> codeList = new ArrayList<Integer>(Arrays.asList(codeArray));
        List<String> nameList = new ArrayList<String>(Arrays.asList(nameArray));
        List<Majiang> majiangList = new ArrayList<>(128);

        for (int i = 1; i <= 128; i++) {
            Majiang majiang = new Majiang(i, codeList.get((i - 1) / 4), nameList.get((i - 1) / 4));
            majiangList.add(majiang);
        }
        return majiangList;
    }

    // 测试特定情况下的麻将局
    public static void shuffleTest(List<Majiang> majiangList) {
        for (int i = 0; i < 16; i++) {
            if (i == 5) continue;
            for (int j = 16; j < 128; j++) {
                if (majiangList.get(j).getCode() == i) {
                    Majiang tmp = majiangList.get(i);
                    majiangList.set(i, majiangList.get(j));
                    majiangList.set(j, tmp);
                }
            }
        }
        for (int i = 16; i < 64; i++) {
            if (majiangList.get(i).getCode() == 5) {
                Majiang tmp = majiangList.get(i);
                majiangList.set(i, majiangList.get(128 - i));
                majiangList.set(128 - i, tmp);
            }
        }
    }

    /**
     * 开始新游戏
     * @param bankerName 庄家的名称
     * @param bankerNo 当前串数
     * @param userList 所有玩家的列表
     * @return 新游戏对象
     */
    public static Game newGame(String bankerName, Integer bankerNo, List<User> userList) {
        List<Majiang> majiangList = generateMajiangList();
        // 麻将洗牌
        Collections.shuffle(majiangList);
        // shuffleTest(majiangList);

        for (int i = 0; i < userList.size(); i++) {
            User user = userList.get(i);
            user.setBanker(false);
            user.setOutList(new ArrayList<>());
            user.setFlowerList(new ArrayList<>());

            user.setUserMajiangList(new ArrayList<>(majiangList.subList(16 * i, 16 * (i + 1))).stream().sorted(Comparator.comparing(Majiang::getId)).collect(Collectors.toList()));
            if (user.getUserNickName().equals(bankerName)) {
                user.addMajiang(majiangList.get(64));
                user.setBanker(true);
                user.sortMajiangList();
            }
        }

        majiangList = new ArrayList<>(majiangList.subList(65, 128));
        Game game = new Game(userList, majiangList, bankerName, bankerNo);

        // 判断谁先补花
        User bankerUser = userList.stream().filter(user -> user.getUserNickName().equals(bankerName)).collect(Collectors.toList()).get(0);
        if (bankerUser.getUserMajiangList().get(16).getCode() > 30) {
            game.setCurrentUserName(bankerUser.getUserNickName());
        } else if (userList.get((bankerUser.getIndex() + 1) % 4).getUserMajiangList().get(15).getCode() > 30) {
            game.setCurrentUserName(userList.get((bankerUser.getIndex() + 1) % 4).getUserNickName());
        } else if (userList.get((bankerUser.getIndex() + 2) % 4).getUserMajiangList().get(15).getCode() > 30) {
            game.setCurrentUserName(userList.get((bankerUser.getIndex() + 2) % 4).getUserNickName());
        } else if (userList.get((bankerUser.getIndex() + 3) % 4).getUserMajiangList().get(15).getCode() > 30) {
            game.setCurrentUserName(userList.get((bankerUser.getIndex() + 3) % 4).getUserNickName());
        } else {
            // 如果大家都没花，则还是庄家出牌
            game.setCurrentUserName(bankerUser.getUserNickName());
        }
        return game;
    }

    public static boolean canChi(List<Majiang> userMajiangList, Integer newCode) {
        List<Integer> majiangCodes = userMajiangList.stream().sorted(Comparator.comparing(Majiang::getId)).filter(majiang -> !majiang.isShow()).map(Majiang::getCode).collect(Collectors.toList());
        if ((majiangCodes.contains(newCode - 2) && majiangCodes.contains(newCode - 1)) ||
                (majiangCodes.contains(newCode - 1) && majiangCodes.contains(newCode + 1)) ||
                (majiangCodes.contains(newCode + 1) && majiangCodes.contains(newCode + 2))) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean canPeng(List<Majiang> userMajiangList, Integer newCode) {
        List<Integer> majiangCodes = userMajiangList.stream().sorted(Comparator.comparing(Majiang::getId)).filter(majiang -> !majiang.isShow()).map(Majiang::getCode).collect(Collectors.toList());
        if (majiangCodes.stream().filter(integer -> integer.equals(newCode)).count() >= 2) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean canGang(List<Majiang> userMajiangList, Integer newCode) {
        List<Integer> majiangCodes = userMajiangList.stream().sorted(Comparator.comparing(Majiang::getId)).filter(majiang -> !majiang.isShow()).map(Majiang::getCode).collect(Collectors.toList());
        if (majiangCodes.stream().filter(integer -> integer.equals(newCode)).count() == 3) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean canAnGang(List<Majiang> userMajiangList, Integer code) {
        List<Integer> majiangCodes = userMajiangList.stream().sorted(Comparator.comparing(Majiang::getId)).filter(majiang -> !majiang.isShow()).map(Majiang::getCode).collect(Collectors.toList());
        if (majiangCodes.stream().filter(integer -> integer.equals(code)).count() == 4) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean canJinQueWithNewMajiang(List<Majiang> userMajiangList, Majiang newMajiang) {
        List<Majiang> tmpMajiangList = new ArrayList<>(userMajiangList);
        tmpMajiangList.add(newMajiang);

        int jinNum = (int) tmpMajiangList.stream().filter(Majiang::isJin).count();
        if (jinNum == 2) {
            System.out.print("判断是不是金雀：");
            List<Integer> majiangCodes = tmpMajiangList.stream().filter(majiang -> !majiang.isJin() && !majiang.isShow() && !majiang.isAnGang()).sorted(Comparator.comparing(Majiang::getCode)).map(Majiang::getCode).collect(Collectors.toList());
            System.out.println(majiangCodes);
            return canHuWithoutQue(majiangCodes);
        }
        return false;
    }

    public static boolean canHuWithQiangJin(List<Majiang> userMajiangList, Majiang newMajiang) {
        List<Majiang> tmpMajiangList = new ArrayList<>(userMajiangList);
        tmpMajiangList.add(newMajiang);
        boolean canHu = canHu(tmpMajiangList, false);
        return canHu;
    }

    public static boolean canHuWithNewMajiang(List<Majiang> userMajiangList, Majiang newMajiang) {
        if (newMajiang == null || newMajiang.getCode() == null) {
            return false;
        }
        List<Majiang> tmpMajiangList = new ArrayList<>(userMajiangList);
        tmpMajiangList.add(newMajiang);
        boolean canHu = canHu(tmpMajiangList, true);
        System.out.println();
        return canHu;
    }

    public static boolean isSpecialSituation(List<Majiang> userMajiangList) {
        return isJinQue(userMajiangList) || isXianJin(userMajiangList);
    }

    public static boolean isJinQue(List<Majiang> userMajiangList) {
        List<Majiang> jinList = userMajiangList.stream().filter(Majiang::isJin).collect(Collectors.toList());
        if (jinList.size() != 2) {
            return false;
        }
        List<Integer> majiangCodes = userMajiangList.stream().filter(majiang -> !majiang.isJin() && !majiang.isShow() && !majiang.isAnGang()).sorted(Comparator.comparing(Majiang::getCode)).map(Majiang::getCode).collect(Collectors.toList());
        for (Integer code1 : mjCodeArray) {
            List<Integer> list1 = new ArrayList<>(majiangCodes);
            list1.add(code1);
            Collections.sort(list1);
            System.out.println("判断canHuWithoutQue" + list1);
            if (canHuWithoutQue(list1)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isXianJin(List<Majiang> userMajiangList) {
        List<Majiang> jinList = userMajiangList.stream().filter(Majiang::isJin).collect(Collectors.toList());
        if (jinList.size() != 1) {
            return false;
        }
        List<Integer> majiangCodes = userMajiangList.stream().filter(majiang -> !majiang.isJin() && !majiang.isShow() && !majiang.isAnGang()).sorted(Comparator.comparing(Majiang::getCode)).map(Majiang::getCode).collect(Collectors.toList());
        return canHuWithoutQue(majiangCodes);
    }

    public static boolean isJinKeng(List<Majiang> userMajiangList) {
        List<Majiang> jinList = userMajiangList.stream().filter(Majiang::isJin).collect(Collectors.toList());
        if (jinList.size() != 1) {
            return false;
        }
        Majiang jin = jinList.get(0);
        List<Integer> majiangCodes = userMajiangList.stream().filter(majiang -> !majiang.isJin() && !majiang.isShow() && !majiang.isAnGang()).sorted(Comparator.comparing(Majiang::getCode)).map(Majiang::getCode).collect(Collectors.toList());

        // 去掉金两侧的牌再判断是否能胡
        Optional<Integer> left = majiangCodes.stream().filter(majiangCode -> majiangCode == jin.getCode() - 1).findFirst();
        Optional<Integer> right = majiangCodes.stream().filter(majiangCode -> majiangCode == jin.getCode() + 1).findFirst();
        if (left.isPresent() && right.isPresent()) {
            Integer leftInteger = left.get();
            Integer rightInteger = right.get();
            majiangCodes.remove(leftInteger);
            majiangCodes.remove(rightInteger);
            return canHuWithQue(majiangCodes);
        }
        return false;
    }

    public static boolean canHu(List<Majiang> userMajiangList, boolean shouldCheck3Jin) {
        System.out.print(userMajiangList.stream().map(Majiang::getCode).collect(Collectors.toList()));
        List<Majiang> majiangList = userMajiangList.stream().filter(majiang -> !majiang.isShow() && !majiang.isAnGang()).collect(Collectors.toList());
        List<Majiang> jinList = majiangList.stream().filter(Majiang::isJin).collect(Collectors.toList());
        List<Integer> majiangCodes = majiangList.stream().filter(majiang -> !majiang.isJin()).sorted(Comparator.comparing(Majiang::getCode)).map(Majiang::getCode).collect(Collectors.toList());

        if (jinList.size() == 3) {
            if (shouldCheck3Jin) {
                // 三头金直接胡
                return true;
            } else {
                // 抢金到三头金时的判断
                for (Integer code1 : mjCodeArray) {
                    majiangCodes.add(code1);
                    for (Integer code2 : mjCodeArray) {
                        majiangCodes.add(code2);
                        for (Integer code3: mjCodeArray) {
                            majiangCodes.add(code3);
                            Collections.sort(majiangCodes);
                            if (canHuWithQue(majiangCodes)) {
                                return true;
                            }
                            majiangCodes.remove(code3);
                        }
                        majiangCodes.remove(code2);
                    }
                    majiangCodes.remove(code1);
                }
            }
        }

        if (jinList.size() == 2) {
            for (Integer code1 : mjCodeArray) {
                majiangCodes.add(code1);
                for (Integer code2 : mjCodeArray) {
                    majiangCodes.add(code2);
                    Collections.sort(majiangCodes);
                    if (canHuWithQue(majiangCodes)) {
                        return true;
                    } else {
                        majiangCodes.remove(code2);
                    }
                }
                majiangCodes.remove(code1);
            }
        }

        if (jinList.size() == 1) {
            for (Integer code1 : mjCodeArray) {
                majiangCodes.add(code1);
                Collections.sort(majiangCodes);
                if (canHuWithQue(majiangCodes)) {
                    return true;
                }
                majiangCodes.remove(code1);
            }
        }

        if (jinList.size() == 0) {
            return canHuWithQue(majiangCodes);
        }

        return false;
    }

    /**
     * 判断麻将能否能胡
     * @param majiangCodes 麻将code数组
     * @return true/false
     */
    public static boolean canHuWithQue(List<Integer> majiangCodes) {
        if (majiangCodes.size() == 2) {
            if (majiangCodes.get(0).equals(majiangCodes.get(1))) {
                return true;
            } else {
                return false;
            }
        } else {
            for (int i = 0; i < majiangCodes.size() - 1; i++) {
                List<Integer> list1 = new ArrayList<>(majiangCodes);
                if (list1.get(i).equals(list1.get(i + 1))) {
                    list1.remove(i);
                    list1.remove(i);
                    if (canHuWithoutQue(list1)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * 判断麻将去掉雀后能否能胡
     * @param majiangCodes 麻将code数组
     * @return true/false
     */
    public static boolean canHuWithoutQue(List<Integer> majiangCodes) {
        if (majiangCodes.size() == 0) {
            return true;
        }
        if (majiangCodes.get(0).equals(majiangCodes.get(1)) && majiangCodes.get(1).equals(majiangCodes.get(2))) {
            majiangCodes.remove(0);
            majiangCodes.remove(0);
            majiangCodes.remove(0);
            return canHuWithoutQue(majiangCodes);
        } else {
            int index0 = 0;
            int index1 = majiangCodes.indexOf(majiangCodes.get(0) + 1);
            int index2 = majiangCodes.indexOf(majiangCodes.get(0) + 2);

            if (index1 != -1 && index2 != -1) {
                majiangCodes.remove(index0);
                majiangCodes.remove(index1 - 1);
                majiangCodes.remove(index2 - 2);
                return canHuWithoutQue(majiangCodes);
            } else {
                return false;
            }
        }
    }

    public static void countTie(List<User> userList) {
        for (User user : userList) {
            user.setScoreChange(0);
        }
    }

    public static void countScore(List<User> userList, User huUser, int moneyNum) {
        for (int i = 0; i < userList.size(); i++) {
            if (i == huUser.getIndex()) {
                userList.get(i).setScore(userList.get(i).getScore() + 3 * moneyNum);
                userList.get(i).setScoreChange(3 * moneyNum);
            } else {
                userList.get(i).setScore(userList.get(i).getScore() - moneyNum);
                userList.get(i).setScoreChange(-moneyNum);
            }
        }
    }

    public static int calculateScore(User huUser, String type, Game currentGame) {
        List<Majiang> majiangList = huUser.getUserMajiangList();

        int flowerNum = huUser.getFlowerList().size();
        int anGangNum = (int) majiangList.stream().filter(Majiang::isAnGang).count() / 2;
        int jinNum = (int) majiangList.stream().filter(Majiang::isJin).count();

        List<Majiang> showList = majiangList.stream().filter(Majiang::isShow).collect(Collectors.toList());

        int mingGangNum = 0;
        for (int i = 0; i < showList.size() - 3; i++) {
            if (showList.get(i).getCode().equals(showList.get(i + 1).getCode()) && showList.get(i + 1).getCode().equals(showList.get(i + 2).getCode()) && showList.get(i + 2).getCode().equals(showList.get(i + 3).getCode())) {
                mingGangNum++;
            }
        }

        int baseScore = 5 + flowerNum + anGangNum + jinNum + mingGangNum;
        int finalScore = baseScore;

        switch (type) {
            case "抢金": {
                finalScore = 20 + 2 * baseScore;
                currentGame.setMessageType(HU_QIANG_JIN);
                break;
            }
            case "平胡": {
                finalScore = baseScore;
                currentGame.setMessageType(HU_PING_HU);
                break;
            }
            case "自摸": {
                finalScore = 2 * baseScore;
                currentGame.setMessageType(HU_ZI_MO);
                // 第一个判断语句用于防止开盘做庄直接三头金胡牌，不存在currentInMajiang导致的bug
                if (currentGame.getCurrentInMajiang().isJin() != null && currentGame.getCurrentInMajiang().isJin()) {
                    // 判断是否金坎
                    if (isJinKeng(huUser.getUserMajiangList())) {
                        finalScore = finalScore + 30;
                    }
                }
                break;
            }
        }

        if (jinNum == 3 && currentGame.getMessageType() != HU_QIANG_JIN) {
            finalScore = 20 + 2 * baseScore;
            currentGame.setMessageType(HU_THREE_JIN);
        }

        if (jinNum == 2 && currentGame.getMessageType() != HU_QIANG_JIN) {
            List<Integer> majiangCodes = majiangList.stream().filter(majiang -> !majiang.isJin() && !majiang.isShow() && !majiang.isAnGang()).sorted(Comparator.comparing(Majiang::getCode)).map(Majiang::getCode).collect(Collectors.toList());
            if (canHuWithoutQue(majiangCodes)) {
               // 说明是金雀
                finalScore = 30 + 2 * baseScore;
                currentGame.setMessageType(HU_JIN_QUE);
            }
        }

        // 没花加十分
        if (flowerNum == 0 && mingGangNum == 0 && anGangNum == 0) {
            finalScore += 10;
        }
        return finalScore;
    }
}
