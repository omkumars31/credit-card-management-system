package com.example.ccbilling.web;

import com.example.ccbilling.domain.PaymentStatus;
import com.example.ccbilling.domain.StatementPaymentStatus;
import com.example.ccbilling.dto.CardDtos.CardBalanceResponse;
import com.example.ccbilling.dto.CardDtos.CardResponse;
import com.example.ccbilling.dto.CardDtos.EntryResponse;
import com.example.ccbilling.dto.CustomerDtos.CustomerResponse;
import com.example.ccbilling.dto.CustomerDtos.UpdateCustomerRequest;
import com.example.ccbilling.dto.LedgerDtos.LateFeeResponse;
import com.example.ccbilling.dto.PageResponse;
import com.example.ccbilling.dto.PaymentDtos.PaymentMethodRequest;
import com.example.ccbilling.dto.PaymentDtos.PaymentMethodResponse;
import com.example.ccbilling.dto.PaymentDtos.PaymentRequest;
import com.example.ccbilling.dto.PaymentDtos.PaymentResponse;
import com.example.ccbilling.dto.PaymentDtos.PaymentResult;
import com.example.ccbilling.dto.StatementDtos.StatementResponse;
import com.example.ccbilling.security.AppPrincipal;
import com.example.ccbilling.service.BillingEntryService;
import com.example.ccbilling.service.CreditCardService;
import com.example.ccbilling.service.CustomerService;
import com.example.ccbilling.service.LateFeeService;
import com.example.ccbilling.service.PaymentMethodService;
import com.example.ccbilling.service.PaymentService;
import com.example.ccbilling.service.StatementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Everything a logged-in customer can do. The customer id always comes from the session, never from the URL. */
@RestController
@RequestMapping("/api/customers/me")
public class CustomerPortalController {

    private final CustomerService customers;
    private final CreditCardService cards;
    private final BillingEntryService entries;
    private final StatementService statements;
    private final PaymentService payments;
    private final PaymentMethodService methods;
    private final LateFeeService lateFees;

    public CustomerPortalController(CustomerService customers, CreditCardService cards, BillingEntryService entries,
                                    StatementService statements, PaymentService payments,
                                    PaymentMethodService methods, LateFeeService lateFees) {
        this.customers = customers;
        this.cards = cards;
        this.entries = entries;
        this.statements = statements;
        this.payments = payments;
        this.methods = methods;
        this.lateFees = lateFees;
    }

    @GetMapping
    public CustomerResponse profile(@AuthenticationPrincipal AppPrincipal me) {
        return customers.getProfile(me.getId());
    }

    @PutMapping
    public CustomerResponse updateProfile(@AuthenticationPrincipal AppPrincipal me,
                                          @Valid @RequestBody UpdateCustomerRequest request) {
        return customers.updateProfile(me.getId(), request);
    }

    // ---- cards ----

    @GetMapping("/cards")
    public List<CardResponse> cards(@AuthenticationPrincipal AppPrincipal me) {
        return cards.listForCustomer(me.getId());
    }

    @GetMapping("/cards/{cardId}")
    public CardResponse card(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long cardId) {
        return cards.getForCustomer(me.getId(), cardId);
    }

    @GetMapping("/cards/{cardId}/balance")
    public CardBalanceResponse balance(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long cardId) {
        return cards.balanceForCustomer(me.getId(), cardId);
    }

    @GetMapping("/cards/{cardId}/entries")
    public PageResponse<EntryResponse> entries(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long cardId,
                                               @PageableDefault(sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return entries.listForCustomer(me.getId(), cardId, pageable);
    }

    // ---- statements ----

    @GetMapping("/statements")
    public PageResponse<StatementResponse> statements(@AuthenticationPrincipal AppPrincipal me,
                                                      @RequestParam(required = false) Long cardId,
                                                      @RequestParam(required = false) StatementPaymentStatus status,
                                                      @PageableDefault(sort = "periodEnd", direction = Sort.Direction.DESC) Pageable pageable) {
        return statements.searchForCustomer(me.getId(), cardId, status, pageable);
    }

    @GetMapping("/statements/{statementId}")
    public StatementResponse statement(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long statementId) {
        return statements.getForCustomer(me.getId(), statementId);
    }

    // ---- payments ----

    /** Requires an Idempotency-Key header. Replaying the same key and body returns the original payment (HTTP 200). */
    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> pay(@AuthenticationPrincipal AppPrincipal me,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                               @Valid @RequestBody PaymentRequest request) {
        PaymentResult result = payments.pay(me.getId(), idempotencyKey, request);
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replay", "true").body(result.payment());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.payment());
    }

    @GetMapping("/payments")
    public PageResponse<PaymentResponse> payments(@AuthenticationPrincipal AppPrincipal me,
                                                  @RequestParam(required = false) Long statementId,
                                                  @RequestParam(required = false) PaymentStatus status,
                                                  @PageableDefault(sort = "initiatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return payments.searchForCustomer(me.getId(), statementId, status, pageable);
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentResponse payment(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long paymentId) {
        return payments.getForCustomer(me.getId(), paymentId);
    }

    // ---- payment methods ----

    @PostMapping("/payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentMethodResponse addMethod(@AuthenticationPrincipal AppPrincipal me,
                                           @Valid @RequestBody PaymentMethodRequest request) {
        return methods.add(me.getId(), request);
    }

    @GetMapping("/payment-methods")
    public List<PaymentMethodResponse> listMethods(@AuthenticationPrincipal AppPrincipal me) {
        return methods.list(me.getId());
    }

    @DeleteMapping("/payment-methods/{methodId}")
    public PaymentMethodResponse deactivateMethod(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long methodId) {
        return methods.deactivate(me.getId(), methodId);
    }

    // ---- late fees ----

    @GetMapping("/late-fees")
    public PageResponse<LateFeeResponse> lateFees(@AuthenticationPrincipal AppPrincipal me,
                                                  @RequestParam(required = false) Long statementId,
                                                  @RequestParam(required = false) Long cardId,
                                                  @PageableDefault(sort = "assessedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return lateFees.searchForCustomer(me.getId(), statementId, cardId, pageable);
    }
}
