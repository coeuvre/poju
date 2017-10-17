package me.coeuvre.poju.manager

import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import java.awt.Color

data class RowDef<T>(
    val name: String,
    val get: Function1<T, String>,
    val set: Function2<T, String, T>
)

data class QueryPagedItemsRequest(val currentPage: Int, val pageCount: Int, val pageSize: Int, val totalCount: Int)

data class QueryPagedItemsResponse<T>(val currentPage: Int, val itemList: List<T>)

data class GetItemApplyFormDetailRequest<T>(
    val currentIndex: Int,
    val totalCount: Int,
    val item: T
)

data class GetItemApplyFormDetailResponse<T>(
    val itemApplyFormDetail: T,
    val isSuccess: Boolean,
    val errorMessage: String?
)

@Component
class JuLikeFlowManager {
    fun <I, D> exportItemApplyFormDetails(queryTotalCount: () -> Mono<Int>,
                                          queryPagedItems: (QueryPagedItemsRequest) -> Mono<QueryPagedItemsResponse<I>>,
                                          getItemApplyFormDetail: (GetItemApplyFormDetailRequest<I>) -> Mono<GetItemApplyFormDetailResponse<D>>,
                                          rowDefs: List<RowDef<D>>): Mono<XSSFWorkbook> =
        queryTotalCount().flatMapMany { totalCount ->
            val pageSize = 20
            val pageCount = Math.ceil(totalCount * 1.0 / pageSize).toInt()
            val queryItemsRequestList = (1..pageCount).map {
                QueryPagedItemsRequest(currentPage = it, pageCount = pageCount, pageSize = pageSize, totalCount = totalCount)
            }
            queryItemsRequestList.toFlux()
                .flatMapSequential { queryPagedItems(it) }
                .flatMapSequential { queryItemsResponse ->
                    queryItemsResponse.itemList.withIndex().toFlux().flatMap { (index, item) ->
                        getItemApplyFormDetail(GetItemApplyFormDetailRequest((queryItemsResponse.currentPage - 1) * pageSize + index + 1, totalCount, item))
                    }
                }
        }.collectList().map { getItemApplyFormDetailResult ->
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet()

            val titleStyle = workbook.createCellStyle()
            val font = workbook.createFont()
            font.setColor(XSSFColor(Color(255, 255, 255)))
            titleStyle.setFont(font)
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
            titleStyle.setFillForegroundColor(XSSFColor(Color(0, 0, 0)))

            val errorStyle = workbook.createCellStyle()
            errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
            errorStyle.setFillForegroundColor(XSSFColor(Color(255, 199, 206)))

            // Create title row
            val titleRow = sheet.createRow(0)
            rowDefs.withIndex().forEach { (cellIndex, rowDef) ->
                val cell = titleRow.createCell(cellIndex)
                cell.cellStyle = titleStyle
                cell.setCellValue(rowDef.name)
            }

            getItemApplyFormDetailResult.withIndex().forEach { (rowIndex, result) ->
                val row = sheet.createRow(1 + rowIndex)
                rowDefs.withIndex().forEach { (cellIndex, rowDef) ->
                    val cell = row.createCell(cellIndex)
                    if (!result.isSuccess) {
                        cell.cellStyle = errorStyle
                    }
                    cell.setCellValue(rowDef.get(result.itemApplyFormDetail))
                }
                if (!result.isSuccess) {
                    row.createCell(rowDefs.size).setCellValue(result.errorMessage)
                }
            }

            workbook
        }
}