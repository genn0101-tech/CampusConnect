package com.campusconnect.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Remembers which groups this browser has unlocked with the right passphrase.
 * The list lives in the HTTP session (server memory, linked to the browser by a cookie).
 * This is not a full login system: there are no user accounts, only "this browser knows the passphrase".
 */
@Component
public class GroupAccess {

    private static final String SESSION_KEY = "unlockedGroups";

    public boolean isUnlocked(HttpSession session, String groupId) {
        return unlockedGroups(session).contains(groupId);
    }

    public void unlock(HttpServletRequest request, String groupId) {
        request.getSession(); // make sure a session exists
        // Give the browser a new session ID after unlocking, so an ID someone else
        // planted earlier becomes useless (protection against "session fixation")
        request.changeSessionId();
        unlockedGroups(request.getSession()).add(groupId);
    }

    public void lock(HttpSession session, String groupId) {
        unlockedGroups(session).remove(groupId);
    }

    @SuppressWarnings("unchecked") // the session only stores Object, we know we put a Set<String> there
    private Set<String> unlockedGroups(HttpSession session) {
        Set<String> groups = (Set<String>) session.getAttribute(SESSION_KEY);
        if (groups == null) {
            groups = new HashSet<>();
            session.setAttribute(SESSION_KEY, groups);
        }
        return groups;
    }
}
