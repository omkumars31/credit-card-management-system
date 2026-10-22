package com.example.ccbilling.web;

import com.example.ccbilling.domain.ActorType;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.CardStatus;
import com.example.ccbilling.domain.CustomerStatus;
import com.example.ccbilling.domain.PaymentStatus;
import com.example.ccbilling.domain.StatementPaymentStatus;
import com.example.ccbilling.dto.AdminDtos.AdminResponse;
import com.example.ccbilling.dto.AdminDtos.CreateAdminRequest;
import com.example.ccbilling.dto.AdminDtos.SetEnabledRequest;
import com.example.ccbilling.dto.BinDtos.BinConfigRequest;
import com.example.ccbilling.dto.BinDtos.BinConfigResponse;
import com.example.ccbilling.dto.CardDtos.CardBalanceResponse;
import com.example.ccbilling.dto.CardDtos.CardResponse;
import com.example.ccbilling.dto.CardDtos.CardStatusRequest;
import com.example.ccbilling.dto.CardDtos.CreateCardRequest;
import com.example.ccbilling.dto.CardDtos.CreditLimitRequest;
import com.example.ccbilling.dto.CardDtos.EntryResponse;
import com.example.ccbilling.dto.CardDtos.PostEntryRequest;
import com.example.ccbilling.dto.CustomerDtos.CustomerResponse;
import com.example.ccbilling.dto.CustomerDtos.CustomerStatusRequest;
import com.example.ccbilling.dto.LedgerDtos.AuditLogResponse;
import com.example.ccbilling.dto.LedgerDtos.LateFeeResponse;
import com.example.ccbilling.dto.PageResponse;
import com.example.ccbilling.dto.PaymentDtos.PaymentResponse;
import com.example.ccbilling.dto.PaymentDtos.ReversePaymentRequest;
import com.example.ccbilling.dto.StatementDtos.GenerateStatementRequest;
import com.example.ccbilling.dto.StatementDtos.StatementResponse;
import com.example.ccbilling.security.AppPrincipal;
import com.example.ccbilling.service.AdminAccountService;
import com.example.ccbilling.service.AuditService;
import com.example.ccbilling.service.BillingEntryService;
import com.example.ccbilling.service.BinConfigurationService;
import com.example.ccbilling.service.CreditCardService;
import com.example.ccbilling.service.CustomerService;
import com.example.ccbilling.service.LateFeeService;
import com.example.ccbilling.service.PaymentService;
import com.example.ccbilling.service.StatementService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administrator endpoints. Protected by URL rule (/api/admin/**) and again by @PreAuthorize in the services. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAccountService adminAccounts;
    private final CustomerService customers;
    private final BinConfigurationService bins;
    private final CreditCardService cards;
    private final BillingEntryService entries;
    private final StatementService statements;
    private final LateFeeService lateFees;
    private final PaymentService payments;
    private final AuditService audit;

    public AdminController(AdminAccountService adminAccounts, CustomerService customers, BinConfigurationService bins,
                           CreditCardService cards, BillingEntryService entries, StatementService statements,
                           LateFeeService lateFees, PaymentService payments, AuditService audit) {
        this.adminAccounts = adminAccounts;
        this.customers = customers;
        this.bins = bins;
        this.cards = cards;
        this.entries = entries;
        this.statements = statements;
        this.lateFees = lateFees;
        this.payments = payments;
        this.audit = audit;
    }

    // ---- administrators ----

    @PostMapping("/administrators")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminResponse createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        return adminAccounts.create(request);
    }

    @GetMapping("/administrators")
    public PageResponse<AdminResponse> listAdmins(@PageableDefault(sort = "id") Pageable pageable) {
        return adminAccounts.list(pageable);
    }

    @PatchMapping("/administrators/{id}/enabled")
    public AdminResponse setAdminEnabled(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id,
                                         @Valid @RequestBody SetEnabledRequest request) {
        return adminAccounts.setEnabled(id, request.enabled(), me.getId());
    }

    // ---- customers ----

    @GetMapping("/customers")
    public PageResponse<CustomerResponse> listCustomers(@RequestParam(required = false) String email,
                                                        @RequestParam(required = false) CustomerStatus status,
                                                        @PageableDefault(sort = "id") Pageable pageable) {
        return customers.search(email, status, pageable);
    }

    @GetMapping("/customers/{id}")
    public CustomerResponse getCustomer(@PathVariable Long id) {
        return customers.get(id);
    }

    @PatchMapping("/customers/{id}/status")
    public CustomerResponse setCustomerStatus(@PathVariable Long id, @Valid @RequestBody CustomerStatusRequest request) {
        return customers.changeStatus(id, request.status());
    }

    // ---- BIN configurations ----

    @PostMapping("/bin-configurations")
    @ResponseStatus(HttpStatus.CREATED)
    public BinConfigResponse createBin(@Valid @RequestBody BinConfigRequest request) {
        return bins.create(request);
    }

    @PutMapping("/bin-configurations/{id}")
    public BinConfigResponse updateBin(@PathVariable Long id, @Valid @RequestBody BinConfigRequest request) {
        return bins.update(id, request);
    }

    @GetMapping("/bin-configurations/{id}")
    public BinConfigResponse getBin(@PathVariable Long id) {
        return bins.get(id);
    }

    @GetMapping("/bin-configurations")
    public PageResponse<BinConfigResponse> listBins(@PageableDefault(sort = "id") Pageable pageable) {
        return bins.list(pageable);
    }

    // ---- cards and billing entries ----

    @PostMapping("/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse issueCard(@Valid @RequestBody CreateCardRequest request) {
        return cards.issue(request);
    }

    @GetMapping("/cards")
    public PageResponse<CardResponse> listCards(@RequestParam(required = false) Long customerId,
                                                @RequestParam(required = false) CardStatus status,
                                                @PageableDefault(sort = "id") Pageable pageable) {
        return cards.search(customerId, status, pageable);
    }

    @GetMapping("/cards/{id}")
    public CardResponse getCard(@PathVariable Long id) {
        return cards.get(id);
    }

    @PatchMapping("/cards/{id}/status")
    public CardResponse setCardStatus(@PathVariable Long id, @Valid @RequestBody CardStatusRequest request) {
        return cards.changeStatus(id, request.status());
    }

    @PutMapping("/cards/{id}/credit-limit")
    public CardResponse setCreditLimit(@PathVariable Long id, @Valid @RequestBody CreditLimitRequest request) {
        return cards.changeCreditLimit(id, request.creditLimit());
    }

    @GetMapping("/cards/{id}/balance")
    public CardBalanceResponse cardBalance(@PathVariable Long id) {
        return cards.balance(id);
    }

    @PostMapping("/cards/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public EntryResponse postEntry(@PathVariable Long id, @Valid @RequestBody PostEntryRequest request) {
        return entries.post(id, request);
    }

    @GetMapping("/cards/{id}/entries")
    public PageResponse<EntryResponse> listEntries(@PathVariable Long id,
                                                   @PageableDefault(sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return entries.listForCard(id, pageable);
    }

    // ---- statements and late fees ----

    @PostMapping("/statements/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public StatementResponse generateStatement(@Valid @RequestBody GenerateStatementRequest request) {
        return statements.generate(request.cardId(), request.periodEnd());
    }

    @GetMapping("/statements")
    public PageResponse<StatementResponse> listStatements(@RequestParam(required = false) Long cardId,
                                                          @RequestParam(required = false) StatementPaymentStatus status,
                                                          @PageableDefault(sort = "periodEnd", direction = Sort.Direction.DESC) Pageable pageable) {
        return statements.search(cardId, status, pageable);
    }

    @GetMapping("/statements/{id}")
    public StatementResponse getStatement(@PathVariable Long id) {
        return statements.get(id);
    }

    @PostMapping("/statements/{id}/late-fee")
    @ResponseStatus(HttpStatus.CREATED)
    public LateFeeResponse assessLateFee(@PathVariable Long id) {
        return lateFees.assess(id);
    }

    @GetMapping("/late-fees")
    public PageResponse<LateFeeResponse> listLateFees(@RequestParam(required = false) Long statementId,
                                                      @RequestParam(required = false) Long cardId,
                                                      @PageableDefault(sort = "assessedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return lateFees.search(statementId, cardId, pageable);
    }

    // ---- payments ----

    @GetMapping("/payments")
    public PageResponse<PaymentResponse> listPayments(@RequestParam(required = false) Long customerId,
                                                      @RequestParam(required = false) Long statementId,
                                                      @RequestParam(required = false) PaymentStatus status,
                                                      @PageableDefault(sort = "initiatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return payments.searchAll(customerId, statementId, status, pageable);
    }

    @PostMapping("/payments/{id}/reverse")
    public PaymentResponse reversePayment(@PathVariable Long id, @Valid @RequestBody ReversePaymentRequest request) {
        return payments.reverse(id, request.reason());
    }

    // ---- audit log ----

    @GetMapping("/audit-logs")
    public PageResponse<AuditLogResponse> auditLogs(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) ActorType actorType,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return audit.search(action, entityType, entityId, actorType, actorId, from, to, pageable);
    }
}
