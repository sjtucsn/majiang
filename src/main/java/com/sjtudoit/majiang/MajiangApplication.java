package com.sjtudoit.majiang;

import com.sjtudoit.majiang.controller.GameController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Set;

@SpringBootApplication(exclude= {DataSourceAutoConfiguration.class})
public class MajiangApplication {

    public static void main(String[] args) {
        // 定时清理线程
        SpringApplication.run(MajiangApplication.class, args);
        new Thread(new ClearThread()).run();
    }

    private static class ClearThread implements Runnable {

        @Override
        public void run() {
            try {
                Thread.sleep(600000);
                Set<GameController> webSocketSet = GameController.webSocketSet;
                boolean clear = true;
                if (webSocketSet.size() > 0) {
                    // 若全场只剩机器人，则清除所有连接
                    for (GameController gameController : webSocketSet) {
                        if (gameController.getRobotClient() == null) {
                            clear = false;
                            break;
                        }
                    }
                    if (clear) {
                        System.out.println("10分钟内无用户返回，关闭所有机器人连接");
                        for (GameController gameController : webSocketSet) {
                            gameController.getSession().close();
                        }
                    }
                }
                new Thread(new ClearThread()).run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
