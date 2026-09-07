package com.curriculum.platform;

import com.curriculum.identity.Passwords;
import com.curriculum.platform.Models.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
public class Bootstrap implements ApplicationRunner {
    private final Commands commands;
    private final Store db;
    private final Passwords passwords;
    public Bootstrap(Commands commands, Store db, Passwords passwords) { this.commands = commands; this.db = db; this.passwords = passwords; }
    @Override public void run(ApplicationArguments args) {
        String initialHash = passwords.encode("admin");
        commands.system(() -> {
            if (db.count("select count(a) from Account a where a.account='admin'") == 0) {
                Account admin = new Account(); admin.account = "admin"; admin.role = "ADMIN"; admin.name = "管理员";
                admin.passwordHash = initialHash; admin.createdAt = db.now(); admin.updatedAt = admin.createdAt; db.save(admin);
            }
            ensureYears(); return null;
        });
    }
    public void ensureYears() {
        int current = Rules.academicYear(db.now().atZone(Rules.SCHOOL_ZONE).toLocalDate());
        for (int year = current; year <= current + 1; year++) for (String season : new String[]{"AUTUMN", "SPRING"}) {
            if (db.count("select count(s) from Semester s where s.academicYear=?1 and s.season=?2", year, season) == 0) {
                Semester semester = new Semester(); semester.academicYear = year; semester.season = season;
                semester.startsOn = Rules.start(year, season); semester.endsOnExclusive = semester.startsOn.plusDays(84); semester.createdAt = db.now(); db.save(semester);
            }
        }
    }
    @Scheduled(fixedDelay = 3600000) public void maintain() {
        commands.system(() -> {
            ensureYears();
            db.update("UPDATE operation_receipt SET result_data=NULL, outcome='EXPIRED' WHERE expires_at <= UTC_TIMESTAMP(6) AND result_data IS NOT NULL LIMIT 1000");
            db.update("DELETE FROM auth_session WHERE absolute_expires_at < UTC_TIMESTAMP(6) LIMIT 1000");
            db.update("DELETE FROM browser_context WHERE expires_at < UTC_TIMESTAMP(6) AND id NOT IN (SELECT context_id FROM auth_session) LIMIT 1000");
            return null;
        });
    }
}
