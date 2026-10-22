package com.example.ccbilling.security;

import com.example.ccbilling.domain.ActorType;
import com.example.ccbilling.domain.CustomerStatus;
import com.example.ccbilling.repository.AdministratorRepository;
import com.example.ccbilling.repository.CustomerRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads customers (by email) and administrators (by username). Names look like "CUSTOMER:a@b.com" / "ADMIN:alice". */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final CustomerRepository customers;
    private final AdministratorRepository administrators;

    public AppUserDetailsService(CustomerRepository customers, AdministratorRepository administrators) {
        this.customers = customers;
        this.administrators = administrators;
    }

    public static String qualify(ActorType type, String identifier) {
        return type.name() + ":" + identifier;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String qualifiedName) {
        int idx = qualifiedName.indexOf(':');
        if (idx < 0) {
            throw new UsernameNotFoundException("Unknown user");
        }
        String type = qualifiedName.substring(0, idx);
        String name = qualifiedName.substring(idx + 1);
        if (ActorType.CUSTOMER.name().equals(type)) {
            return customers.findByEmail(name.toLowerCase())
                    .map(c -> new AppPrincipal(c.getId(), ActorType.CUSTOMER, c.getEmail(), c.getFullName(),
                            c.getPasswordHash(), c.getStatus() == CustomerStatus.ACTIVE))
                    .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        }
        if (ActorType.ADMIN.name().equals(type)) {
            return administrators.findByUsername(name)
                    .map(a -> new AppPrincipal(a.getId(), ActorType.ADMIN, a.getUsername(), a.getFullName(),
                            a.getPasswordHash(), a.isEnabled()))
                    .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        }
        throw new UsernameNotFoundException("Unknown user");
    }
}
