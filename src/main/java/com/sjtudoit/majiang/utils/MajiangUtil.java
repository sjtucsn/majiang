package com.sjtudoit.majiang.utils;

import com.sjtudoit.majiang.dto.Game;
import com.sjtudoit.majiang.dto.Majiang;
import com.sjtudoit.majiang.dto.User;

import java.util.*;
import java.util.stream.Collectors;

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

        // 麻将洗牌
        Collections.shuffle(majiangList);
        return majiangList;
    }
    /**
     * 开始新游戏
     * @param id 庄家的sessionId
     * @param name 庄家的名称
     * @param userMap 所有玩家Map
     * @return 新游戏对象
     */
    public static Game newGame(String id, String name, Map<String, String> userMap) {
        List<Majiang> majiangList = generateMajiangList();

        Map<String, String> tmpMap = new HashMap<>(userMap);
        tmpMap.remove(id);
        Iterator<String> iterator = tmpMap.values().iterator();

        // user1是庄家
        User user1 = new User(0, name, new ArrayList<>(majiangList.subList(0, 17)).stream().sorted(Comparator.comparing(Majiang::getId)).collect(Collectors.toList()));
        User user2 = new User(1, iterator.next(), new ArrayList<>(majiangList.subList(17, 33)).stream().sorted(Comparator.comparing(Majiang::getId)).collect(Collectors.toList()));
        User user3 = new User(2, iterator.next(), new ArrayList<>(majiangList.subList(33, 49)).stream().sorted(Comparator.comparing(Majiang::getId)).collect(Collectors.toList()));
        User user4 = new User(3, iterator.next(), new ArrayList<>(majiangList.subList(49, 65)).stream().sorted(Comparator.comparing(Majiang::getId)).collect(Collectors.toList()));

        majiangList = new ArrayList<>(majiangList.subList(65, 128));

        List<User> userList = new ArrayList<User>() {{
            add(user1);
            add(user2);
            add(user3);
            add(user4);
        }};

        Game game = new Game(userList, majiangList, name);

        // 判断谁先补花
        if (user1.getUserMajiangList().get(16).getCode() > 30) {
            game.setCurrentUserName(user1.getUserNickName());
        } else if (user2.getUserMajiangList().get(15).getCode() > 30) {
            game.setCurrentUserName(user2.getUserNickName());
        } else if (user3.getUserMajiangList().get(15).getCode() > 30) {
            game.setCurrentUserName(user3.getUserNickName());
        } else if (user4.getUserMajiangList().get(15).getCode() > 30) {
            game.setCurrentUserName(user4.getUserNickName());
        } else {
            // 如果大家都没花，则还是user1庄家出牌
            game.setCurrentUserName(user1.getUserNickName());
        }
        return game;
    }

    public static Game newGame(Game previousGame) {
        List<Majiang> majiangList = generateMajiangList();

        List<User> userList = previousGame.getUserList();
        for (int i = 0; i < userList.size(); i++) {
            User user = userList.get(i);
            user.setOutList(new ArrayList<>());
            user.setFlowerList(new ArrayList<>());

            user.setUserMajiangList(new ArrayList<>(majiangList.subList(16 * i, 16 * (i + 1))).stream().sorted(Comparator.comparing(Majiang::getId)).collect(Collectors.toList()));
            if (user.getUserNickName().equals(previousGame.getBankerName())) {
                user.addMajiang(majiangList.get(64));
                user.sortMajiangList();
            }
        }

        majiangList = new ArrayList<>(majiangList.subList(65, 128));
        Game game = new Game(userList, majiangList, previousGame.getBankerName());

        // 判断谁先补花
        User bankerUser = userList.stream().filter(user -> user.getUserNickName().equals(previousGame.getBankerName())).collect(Collectors.toList()).get(0);
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
            System.out.print(majiangCodes);
            return canHuWithoutQue(majiangCodes);
        }
        return false;
    }

    public static boolean canHuWithQiangJin(List<Majiang> userMajiangList, Majiang newMajiang) {
        List<Majiang> tmpMajiangList = new ArrayList<>(userMajiangList);
        tmpMajiangList.add(newMajiang);
        boolean canHu = canHu(tmpMajiangList, false);
        System.out.println();
        return canHu;
    }


    public static boolean canHuWithNewMajiang(List<Majiang> userMajiangList, Majiang newMajiang) {
        List<Majiang> tmpMajiangList = new ArrayList<>(userMajiangList);
        tmpMajiangList.add(newMajiang);
        boolean canHu = canHu(tmpMajiangList, true);
        System.out.println();
        return canHu;
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

    public static void countScore(List<User> userList, User huUser, int moneyNum) {
        for (int i = 0; i < userList.size(); i++) {
            if (i == huUser.getIndex()) {
                userList.get(i).setScore(userList.get(i).getScore() + 3 * moneyNum);
            } else {
                userList.get(i).setScore(userList.get(i).getScore() - moneyNum);
            }
        }
    }

    public static int calculateScore(User huUser, String type) {
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
                break;
            }
            case "平胡": {
                finalScore = baseScore;
                break;
            }
            case "自摸": {
                finalScore = 2 * baseScore;
                break;
            }
        }

        if (jinNum == 3) {
            finalScore = 20 + 2 * baseScore;
        }

        if (jinNum == 2) {
            System.out.println("判断是不是金雀");
            List<Integer> majiangCodes = majiangList.stream().filter(majiang -> !majiang.isJin() && !majiang.isShow() && !majiang.isAnGang()).sorted(Comparator.comparing(Majiang::getCode)).map(Majiang::getCode).collect(Collectors.toList());
            if (canHuWithoutQue(majiangCodes)) {
               // 说明是金雀
               finalScore = 30 + 2 * baseScore;
            }
        }

        if (flowerNum == 0 && mingGangNum == 0 && anGangNum == 0) {
            finalScore += 10;
        }
        return finalScore;
    }
}
