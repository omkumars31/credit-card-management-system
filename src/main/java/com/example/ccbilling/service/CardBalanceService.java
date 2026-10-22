package com.example.ccbilling.service;

import com.example.ccbilling.billing.Money;
import com.example.ccbilling.domain.BillingEntry;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.Statement;
import com.example.ccbilling.dto.CardDtos.CardBalanceResponse;
import com.example.ccbilling.repository.BillingEntryRepository;
import com.example.ccbilling.repository.StatementRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Computes a card's exposure: outstanding balance of the latest statement (statements are cumulative, so older
 * ones are superseded) plus the net of entries that have not been billed yet.
 */
@Service
public class CardBalanceService {

    private final StatementRepository statements;
    private final BillingEntryRepository entries;

    public CardBalanceService(StatementRepository statements, BillingEntryRepository entries) {
        this.statements = statements;
        this.entries = entries;
    }

    public CardBalanceResponse balanceOf(CreditCard card) {
        Optional<Statement> latest = statements.findTopByCardIdOrderByPeriodEndDesc(card.getId());
        BigDecimal statementOutstanding = latest.map(Statement::outstanding).orElse(Money.ZERO);
        BigDecimal unbilledNet = unbilledNet(card.getId());
        BigDecimal exposure = Money.max(Money.ZERO, statementOutstanding.add(unbilledNet));
        BigDecimal available = card.getCreditLimit().subtract(exposure);
        return new CardBalanceResponse(card.getId(), card.getCreditLimit(), latest.map(Statement::getId).orElse(null),
                Money.scale(statementOutstanding), Money.scale(unbilledNet), Money.scale(exposure), Money.scale(available));
    }

    private BigDecimal unbilledNet(Long cardId) {
        List<BillingEntry> unbilled = entries.findByCardIdAndStatementIsNull(cardId);
        BigDecimal net = Money.ZERO;
        for (BillingEntry e : unbilled) {
            net = e.getEntryType().isDebit() ? net.add(e.getAmount()) : net.subtract(e.getAmount());
        }
        return net;
    }
}
