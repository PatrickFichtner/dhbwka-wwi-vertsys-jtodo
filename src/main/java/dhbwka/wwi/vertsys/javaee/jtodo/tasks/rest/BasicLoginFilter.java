/*
 * Copyright © 2019 Dennis Schulmeister-Zimolong
 * 
 * E-Mail: dhbw@windows3.de
 * Webseite: https://www.wpvs.de/
 * 
 * Dieser Quellcode ist lizenziert unter einer
 * Creative Commons Namensnennung 4.0 International Lizenz.
 */
package dhbwka.wwi.vertsys.javaee.jtodo.tasks.rest;

import dhbwka.wwi.vertsys.javaee.jtodo.common.ejb.UserBean;
import dhbwka.wwi.vertsys.javaee.jtodo.common.jpa.User;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;

/**
 * Lösungsvorschlag von Martin Kutscher. Da in der web.xml nur ein
 * Anmeldeverfahren definiert werden kann, programmieren wir den HTTP Basic Auth
 * für den Webservice hier einfach manuell aus.
 *
 * In der web.xml müssen hierfür folgende Zeilen ergänzt werden, um diese Klasse
 * zu konfigurieren:
 *
 *   <filter>
 *     <filter-name>BasicAuthFilter</filter-name>
 *     <filter-class>dhbwka.wwi.vertsys.javaee.jtodo.tasks.rest.BasicLoginFilter</filter-class>
 *   <init-param>
 *     <param-name>role-names-comma-sep</param-name>
 *     <param-value>app-user</param-value>
 *   </init-param>
 *   </filter>
 *     <filter-mapping>
 *     <filter-name>BasicLoginFilter</filter-name>
 *     <url-pattern>/api/*</url-pattern>
 *   </filter-mapping>
 *
 * Vgl.
 * https://stackoverflow.com/questions/27588665/how-do-i-configure-both-basic-and-form-authentication-methods-in-the-same-java-e
 */
public class BasicLoginFilter implements Filter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String FILTER_PARAMETER_ROLE_NAMES_COMMA_SEPARATED = "role-names-comma-sep";
    private static final String ROLE_SEPARATOR = ",";
    private static final String BASIC_AUTH_SEPARATOR = ":";
    
    @EJB
    private UserBean userBean;

    /**
     * Liste der Benutzerrollen, mit denen sich der Anwender authentifizieren
     * muss
     */
    private List<String> roleNames;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String roleNamesParam = filterConfig.getInitParameter(FILTER_PARAMETER_ROLE_NAMES_COMMA_SEPARATED);
        String[] roleNamesParsed = null;

        if (roleNamesParam != null) {
            roleNamesParsed = roleNamesParam.split(ROLE_SEPARATOR);
        }

        if (roleNamesParsed != null) {
            this.roleNames = Arrays.asList(roleNamesParsed);
        }

        if (this.roleNames == null || this.roleNames.isEmpty()) {
            throw new IllegalArgumentException("No roles defined!");
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        // Benutzername und Password aus den Authorozation-Header auslesen
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BASIC_PREFIX)) {
            throw new ServletException("Authorization Header fehlt");
        }

        String autfHeaderUserPwPart = authHeader.substring(BASIC_PREFIX.length());
        if (autfHeaderUserPwPart == null) {
            throw new ServletException("Anmeldung nur über Basic-Auth möglich");
        }

        String headerDecoded = new String(Base64.getDecoder().decode(autfHeaderUserPwPart));
        if (!headerDecoded.contains(BASIC_AUTH_SEPARATOR)) {
            throw new ServletException("Benutzername oder Passwort fehlt");
        }
        String[] userPwPair = headerDecoded.split(BASIC_AUTH_SEPARATOR);
        if (userPwPair.length != 2) {
            throw new ServletException("Benutzername oder Passwort fehlt");
        }
        String userDecoded = userPwPair[0];
        String passDecoded = userPwPair[1];

        request.logout();
        request.login(userDecoded, passDecoded);

        // check roles for the user
        // Logindaten und Rollenzuordnung prüfen
        User user = this.userBean.findByUsername(userDecoded);
        boolean hasRoles = false;
        
        if (user == null) {
            throw new ServletException("Benutzerprofil nicht gefunden");            
        }
        
        for (String role : this.roleNames) {
            if (user.getGroups().contains(role)) {
                hasRoles = true;
                break;
            }
        }

        if (hasRoles) {
            chain.doFilter(request, response);
            //request.logout(); // optional
        } else {
            throw new ServletException("Keine ausreichenden Berechtigungen");
        }
    }

    @Override
    public void destroy() {
    }
}