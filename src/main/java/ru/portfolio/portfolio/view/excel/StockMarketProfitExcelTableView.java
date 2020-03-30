package ru.portfolio.portfolio.view.excel;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;
import ru.portfolio.portfolio.entity.PortfolioEntity;
import ru.portfolio.portfolio.repository.PortfolioRepository;
import ru.portfolio.portfolio.view.Table;
import ru.portfolio.portfolio.view.TableHeader;

import java.util.List;

import static ru.portfolio.portfolio.view.excel.StockMarketProfitExcelTableHeader.*;

@Component
@RequiredArgsConstructor
public class StockMarketProfitExcelTableView extends ExcelTableView {
    private final PortfolioRepository portfolioRepository;
    private final StockMarketProfitExcelTableFactory stockMarketProfitExcelTableFactory;

    @Override
    protected List<PortfolioEntity> getPortfolios() {
        // TODO select by user
        return portfolioRepository.findAll();
    }

    @Override
    protected Table getTable(PortfolioEntity portfolio) {
        return stockMarketProfitExcelTableFactory.create(portfolio);
    }

    @Override
    protected void writeHeader(Sheet sheet, Class<? extends TableHeader> headerType, CellStyle style) {
        super.writeHeader(sheet, headerType, style);
        sheet.setColumnWidth(SECURITY.ordinal(), 45 * 256);
        sheet.setColumnWidth(BUY_AMOUNT.ordinal(), 16 * 256);
        sheet.setColumnWidth(CELL_AMOUNT.ordinal(), 16 * 256);
    }

    @Override
    protected Table.Record getTotalRow() {
        Table.Record totalRow = new Table.Record();
        for (StockMarketProfitExcelTableHeader column : StockMarketProfitExcelTableHeader.values()) {
            totalRow.put(column, "=SUM(" +
                    column.getColumnIndex() + "3:" +
                    column.getColumnIndex() + "100000)");
        }
        totalRow.put(SECURITY, "Итого:");
        totalRow.remove(BUY_DATE);
        totalRow.remove(CELL_DATE);
        totalRow.remove(BUY_PRICE);
        totalRow.remove(PROFIT);
        return totalRow;
    }

    @Override
    protected void sheetPostCreate(Sheet sheet, CellStyles styles) {
        super.sheetPostCreate(sheet, styles);
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            Cell cell = row.getCell(SECURITY.ordinal());
            if (cell != null) {
                cell.setCellStyle(styles.getSecurityNameStyle());
            }
        }
        for (Cell cell : sheet.getRow(1)) {
            if (cell == null) continue;
            if (cell.getColumnIndex() == SECURITY.ordinal()) {
                cell.setCellStyle(styles.getTotalTextStyle());
            } else if (cell.getColumnIndex() == COUNT.ordinal()){
                cell.setCellStyle(styles.getIntStyle());
            } else {
                cell.setCellStyle(styles.getTotalRowStyle());
            }
        }
    }
}