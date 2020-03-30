package ru.portfolio.portfolio.view.excel;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.portfolio.portfolio.converter.SecurityEntityConverter;
import ru.portfolio.portfolio.converter.SecurityEventCashFlowEntityConverter;
import ru.portfolio.portfolio.converter.TransactionEntityConverter;
import ru.portfolio.portfolio.entity.PortfolioEntity;
import ru.portfolio.portfolio.entity.SecurityEntity;
import ru.portfolio.portfolio.entity.SecurityEventCashFlowEntity;
import ru.portfolio.portfolio.entity.TransactionCashFlowEntity;
import ru.portfolio.portfolio.pojo.CashFlowType;
import ru.portfolio.portfolio.pojo.Security;
import ru.portfolio.portfolio.pojo.SecurityEventCashFlow;
import ru.portfolio.portfolio.pojo.Transaction;
import ru.portfolio.portfolio.repository.SecurityEventCashFlowRepository;
import ru.portfolio.portfolio.repository.SecurityRepository;
import ru.portfolio.portfolio.repository.TransactionCashFlowRepository;
import ru.portfolio.portfolio.repository.TransactionRepository;
import ru.portfolio.portfolio.view.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.portfolio.portfolio.view.excel.StockMarketExcelProfitTableHeader.*;

@Component
@RequiredArgsConstructor
public class StockMarketExcelProfitTableFactory implements TableFactory {
    private final TransactionRepository transactionRepository;
    private final SecurityRepository securityRepository;
    private final TransactionCashFlowRepository transactionCashFlowRepository;
    private final TransactionEntityConverter transactionEntityConverter;
    private final SecurityEntityConverter securityEntityConverter;
    private final PaidInterestFactory paidInterestFactory;
    private final SecurityEventCashFlowRepository securityEventCashFlowRepository;
    private final SecurityEventCashFlowEntityConverter securityEventCashFlowEntityConverter;

    public Table create(PortfolioEntity portfolio) {
        Table openPositionsProfit = new Table();
        Table closedPositionsProfit = new Table();
        for (String isin : getSecuritiesIsin(portfolio)) {
            Optional<SecurityEntity> securityEntity = securityRepository.findByIsin(isin);
            if (securityEntity.isPresent()) {
                Positions positions = getPositions(portfolio, securityEntity.get());
                Security security = securityEntityConverter.fromEntity(securityEntity.get());
                PaidInterest paidInterest = paidInterestFactory.create(portfolio.getPortfolio(), security, positions);
                closedPositionsProfit.addAll(getPositionProfit(security, positions.getClosedPositions(),
                        paidInterest, this::getClosedPositionProfit));
                openPositionsProfit.addAll(getPositionProfit(security, positions.getOpenedPositions(),
                        paidInterest, this::getOpenedPositionProfit));
            }
        }
        Table profit = new Table();
        profit.addAll(closedPositionsProfit);
        profit.addAll(openPositionsProfit);
        return profit;
    }

    private Collection<String> getSecuritiesIsin(PortfolioEntity portfolio) {
        return transactionRepository.findDistinctIsinByPortfolioOrderByTimestampAsc(portfolio);
    }

    private Positions getPositions(PortfolioEntity portfolio, SecurityEntity security) {
        Deque<Transaction> transactions = transactionRepository
                .findBySecurityAndPortfolioOrderByTimestampAscIdAsc(security, portfolio)
                .stream()
                .map(transactionEntityConverter::fromEntity)
                .collect(Collectors.toCollection(LinkedList::new));
        Deque<SecurityEventCashFlow> redemption = getRedemption(portfolio, security);
        return new Positions(transactions, redemption);
    }

    private Deque<SecurityEventCashFlow> getRedemption(PortfolioEntity portfolio, SecurityEntity securityEntity) {
        return securityEventCashFlowRepository
                .findByPortfolioAndIsinAndCashFlowTypeOrderByTimestampAsc(
                        portfolio.getPortfolio(),
                        securityEntity.getIsin(),
                        CashFlowType.REDEMPTION)
                .stream()
                .map(securityEventCashFlowEntityConverter::fromEntity)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private <T extends Position> Table getPositionProfit(Security security,
                                                         Deque<T> positions,
                                                         PaidInterest paidInterest,
                                                         Function<T, Table.Record> profitBuilder) {
        Table rows = new Table();
        for (T position : positions) {
            Table.Record record = profitBuilder.apply(position);
            record.putAll(getPaidInterestProfit(position, paidInterest));
            record.put(SECURITY,
                    Optional.ofNullable(security.getName())
                            .orElse(security.getIsin()));
            rows.add(record);
        }
        return rows;
    }

    private Table.Record getOpenedPositionProfit(OpenedPosition position) {
        Table.Record row = new Table.Record();
        Transaction transaction = position.getOpenTransaction();
        row.put(BUY_DATE, transaction.getTimestamp());
        row.put(COUNT, position.getCount());
        row.put(BUY_PRICE, getTransactionCashFlow(transaction, CashFlowType.PRICE, 1d / transaction.getCount()));
        double multipier = Math.abs(1d * position.getCount() / transaction.getCount());
        row.put(BUY_AMOUNT, getTransactionCashFlow(transaction, CashFlowType.PRICE, multipier));
        row.put(BUY_ACCRUED_INTEREST, getTransactionCashFlow(transaction, CashFlowType.ACCRUED_INTEREST, multipier));
        row.put(BUY_COMMISSION, getTransactionCashFlow(transaction, CashFlowType.COMMISSION, multipier));
        return row;
    }

    private Table.Record getClosedPositionProfit(ClosedPosition position) {
        // open transaction info
        Table.Record row = new Table.Record(getOpenedPositionProfit(position));
        // close transaction info
        Transaction transaction = position.getCloseTransaction();
        double multipier = Math.abs(1d * position.getCount() / transaction.getCount());
        row.put(CELL_DATE, transaction.getTimestamp());
        BigDecimal cellAmount;
        switch (position.getClosingEvent()) {
            case PRICE:
                cellAmount = getTransactionCashFlow(transaction, CashFlowType.PRICE, multipier);
                break;
            case REDEMPTION:
                cellAmount = getRedemptionCashFlow(transaction.getPortfolio(), transaction.getIsin(), multipier);
                break;
            default:
                throw new IllegalArgumentException("ЦБ " + transaction.getIsin() +
                        " не может быть закрыта событием типа " + position.getClosingEvent());
        }
        row.put(CELL_AMOUNT, cellAmount);
        row.put(CELL_ACCRUED_INTEREST, getTransactionCashFlow(transaction, CashFlowType.ACCRUED_INTEREST, multipier));
        row.put(CELL_COMMISSION, getTransactionCashFlow(transaction, CashFlowType.COMMISSION, multipier));
        row.put(FORECAST_TAX, getForecastTax());
        row.put(PROFIT, getClosedPositionProfit());
        return row;
    }

    private Table.Record getPaidInterestProfit(Position position,
                                               PaidInterest paidInterest) {
        Table.Record info = new Table.Record();
        info.put(COUPON, convertPaidInterestToExcelFormula(paidInterest.get(CashFlowType.COUPON, position)));
        info.put(AMORTIZATION, convertPaidInterestToExcelFormula(paidInterest.get(CashFlowType.AMORTIZATION, position)));
        info.put(DIVIDEND, convertPaidInterestToExcelFormula(paidInterest.get(CashFlowType.DIVIDEND, position)));
        info.put(TAX, convertPaidInterestToExcelFormula(paidInterest.get(CashFlowType.TAX, position)));
        return info;
    }

    private BigDecimal getTransactionCashFlow(Transaction transaction, CashFlowType type, double multiplier) {
        if (transaction.getId() == null) {
            return null;
        }
        Optional<TransactionCashFlowEntity> cashFlow = transactionCashFlowRepository
                .findByTransactionIdAndCashFlowType(transaction.getId(), type);
        return cashFlow.map(cash -> cash
                .getValue()
                .multiply(BigDecimal.valueOf(multiplier))
                .abs()
                .setScale(2, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private BigDecimal getRedemptionCashFlow(String portfolio, String isin, double multiplier) {
        List<SecurityEventCashFlowEntity> cashFlows = securityEventCashFlowRepository
                .findByPortfolioAndIsinAndCashFlowTypeOrderByTimestampAsc(portfolio, isin, CashFlowType.REDEMPTION);
        if (cashFlows.isEmpty()) {
            return null;
        } else if (cashFlows.size() != 1) {
            throw new IllegalArgumentException("По ЦБ может быть не более одного события погашения, по бумаге " + isin +
                    " найдено " + cashFlows.size() + " событий погашения: " + cashFlows);
        }
        return cashFlows.get(0)
                .getValue()
                .multiply(BigDecimal.valueOf(multiplier))
                .abs()
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static  <T extends Position> String convertPaidInterestToExcelFormula(List<SecurityEventCashFlow> pays) {
        if (pays == null || pays.isEmpty()) {
            return null;
        }
        return pays.stream()
                .map(SecurityEventCashFlow::getValue)
                .map(BigDecimal::abs)
                .map(String::valueOf)
                .collect(Collectors.joining("+", "=", ""));
    }

    private String getForecastTax() {
        String forecastTaxFormula =
                "(" + CELL_AMOUNT.getCellAddr() + "+" + CELL_ACCRUED_INTEREST.getCellAddr() + "+" + AMORTIZATION.getCellAddr() + ")" +
                        "-(" + BUY_AMOUNT.getCellAddr() + "+" + BUY_ACCRUED_INTEREST.getCellAddr() + ")" +
                        "-" + BUY_COMMISSION.getCellAddr() + "-" + CELL_COMMISSION.getCellAddr();
        return "=IF(" + forecastTaxFormula + "<0,0,0.13*(" + forecastTaxFormula + "))";
    }

    private String getClosedPositionProfit() {
        String buy = "(" + BUY_AMOUNT.getCellAddr() + "+" + BUY_ACCRUED_INTEREST.getCellAddr() + "+" + BUY_COMMISSION.getCellAddr() + ")";
        String cell = "(" + CELL_AMOUNT.getCellAddr() + "+" + CELL_ACCRUED_INTEREST.getCellAddr() + "+" +
                COUPON.getCellAddr() + "+" + AMORTIZATION.getCellAddr() + "+" + DIVIDEND.getCellAddr() +
                "-(" + CELL_COMMISSION.getCellAddr() + "+" + TAX.getCellAddr() + "+" + FORECAST_TAX.getCellAddr() + "))";
        String multiplicator = "100*365/DAYS360(" + BUY_DATE.getCellAddr() + "," + CELL_DATE.getCellAddr() + ")";
        return "=((" + cell + "-" + buy + ")/" + buy + ")*" + multiplicator;
    }
}