package com.team.cultureevents.features.commons.config;

import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/** 8080 점유 시 로그에 조치 방법을 남긴다. */
@Component
public class PortInUseHintListener implements ApplicationListener<ApplicationFailedEvent> {

    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        Throwable ex = event.getException();
        while (ex != null) {
            if (ex instanceof PortInUseException
                    || (ex.getMessage() != null && ex.getMessage().contains("Port") && ex.getMessage().contains("in use"))) {
                System.err.println("""
                        
                        [CultureMate] 8080 포트가 이미 사용 중입니다.
                        1) IntelliJ에서 이전 Run을 Stop 하거나
                        2) PowerShell: netstat -ano | findstr :8080  →  taskkill /PID <번호> /F
                        3) 또는 Run Configuration Environment에 SERVER_PORT=8081
                        """);
                return;
            }
            ex = ex.getCause();
        }
    }
}
