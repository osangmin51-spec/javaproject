import java.util.List;

interface StockCategoryProfile {
    String label();

    String riskNote();
}

class SemiconductorCategory implements StockCategoryProfile { public String label() { return "반도체"; } public String riskNote() { return "경기와 설비투자 사이클 영향을 크게 받습니다."; } }
class BatteryCategory implements StockCategoryProfile { public String label() { return "2차전지"; } public String riskNote() { return "전기차 수요와 원재료 가격 변동을 함께 확인해야 합니다."; } }
class BioCategory implements StockCategoryProfile { public String label() { return "바이오"; } public String riskNote() { return "임상 결과와 규제 승인 이벤트에 민감합니다."; } }
class AutoCategory implements StockCategoryProfile { public String label() { return "자동차"; } public String riskNote() { return "환율, 판매량, 원가 구조 영향을 받습니다."; } }
class FinanceCategory implements StockCategoryProfile { public String label() { return "금융"; } public String riskNote() { return "금리와 대손 비용 변화가 중요합니다."; } }
class InternetCategory implements StockCategoryProfile { public String label() { return "인터넷"; } public String riskNote() { return "광고, 커머스, 플랫폼 규제 영향을 받습니다."; } }
class GameCategory implements StockCategoryProfile { public String label() { return "게임"; } public String riskNote() { return "신작 성과와 이용자 지표 변동이 큽니다."; } }
class EntertainmentCategory implements StockCategoryProfile { public String label() { return "엔터테인먼트"; } public String riskNote() { return "아티스트 활동과 콘텐츠 매출 변동성이 있습니다."; } }
class SteelCategory implements StockCategoryProfile { public String label() { return "철강"; } public String riskNote() { return "원재료 가격과 글로벌 수요 영향을 받습니다."; } }
class ShipbuildingCategory implements StockCategoryProfile { public String label() { return "조선"; } public String riskNote() { return "수주 잔고와 원가 관리가 중요합니다."; } }
class DefenseCategory implements StockCategoryProfile { public String label() { return "방산"; } public String riskNote() { return "수출 계약과 정부 예산 변화에 민감합니다."; } }
class ChemicalCategory implements StockCategoryProfile { public String label() { return "화학"; } public String riskNote() { return "스프레드와 유가 변동을 확인해야 합니다."; } }
class EnergyCategory implements StockCategoryProfile { public String label() { return "에너지"; } public String riskNote() { return "유가와 정제마진 변동성이 큽니다."; } }
class TelecomCategory implements StockCategoryProfile { public String label() { return "통신"; } public String riskNote() { return "가입자 지표와 설비투자 부담을 확인해야 합니다."; } }
class RetailCategory implements StockCategoryProfile { public String label() { return "유통"; } public String riskNote() { return "소비 경기와 재고 관리가 중요합니다."; } }
class CosmeticsCategory implements StockCategoryProfile { public String label() { return "화장품"; } public String riskNote() { return "해외 수요와 브랜드 경쟁력이 중요합니다."; } }
class AviationCategory implements StockCategoryProfile { public String label() { return "항공"; } public String riskNote() { return "유가, 환율, 여객 수요에 민감합니다."; } }
class InsuranceCategory implements StockCategoryProfile { public String label() { return "보험"; } public String riskNote() { return "금리와 손해율 변화를 확인해야 합니다."; } }
class SecuritiesCategory implements StockCategoryProfile { public String label() { return "증권"; } public String riskNote() { return "거래대금과 투자심리 영향을 받습니다."; } }
class UtilityCategory implements StockCategoryProfile { public String label() { return "전력"; } public String riskNote() { return "요금 정책과 원가 구조가 중요합니다."; } }
class HoldingCompanyCategory implements StockCategoryProfile { public String label() { return "지주"; } public String riskNote() { return "자회사 실적과 할인율 변화를 봐야 합니다."; } }
class FoodCategory implements StockCategoryProfile { public String label() { return "음식료"; } public String riskNote() { return "원재료 가격과 소비 수요가 중요합니다."; } }
class ConstructionCategory implements StockCategoryProfile { public String label() { return "건설"; } public String riskNote() { return "분양 경기와 원가율 변동에 민감합니다."; } }
class MachineryCategory implements StockCategoryProfile { public String label() { return "기계"; } public String riskNote() { return "수주와 설비투자 사이클을 확인해야 합니다."; } }
class DisplayCategory implements StockCategoryProfile { public String label() { return "디스플레이"; } public String riskNote() { return "패널 가격과 고객사 수요 영향을 받습니다."; } }
class ElectronicsCategory implements StockCategoryProfile { public String label() { return "전자"; } public String riskNote() { return "제품 수요와 부품 원가 변동이 중요합니다."; } }
class MaterialsCategory implements StockCategoryProfile { public String label() { return "소재"; } public String riskNote() { return "원자재 가격과 전방 산업 수요가 중요합니다."; } }
class MedicalCategory implements StockCategoryProfile { public String label() { return "의료기기"; } public String riskNote() { return "인허가와 해외 판매망을 확인해야 합니다."; } }
class LogisticsCategory implements StockCategoryProfile { public String label() { return "물류"; } public String riskNote() { return "운임과 물동량 변화에 민감합니다."; } }
class MediaCategory implements StockCategoryProfile { public String label() { return "미디어"; } public String riskNote() { return "콘텐츠 흥행과 광고 경기를 확인해야 합니다."; } }
class LeisureCategory implements StockCategoryProfile { public String label() { return "레저"; } public String riskNote() { return "소비 경기와 여행 수요 영향을 받습니다."; } }
class ReitsCategory implements StockCategoryProfile { public String label() { return "리츠"; } public String riskNote() { return "금리와 임대 수익 안정성이 중요합니다."; } }
class EtfCategory implements StockCategoryProfile { public String label() { return "ETF"; } public String riskNote() { return "기초지수와 괴리율을 함께 확인해야 합니다."; } }
class EtfLeveragedCategory implements StockCategoryProfile { public String label() { return "레버리지 ETF"; } public String riskNote() { return "복리 효과와 변동성 확대 위험이 있습니다."; } }
class EtfInverseCategory implements StockCategoryProfile { public String label() { return "인버스 ETF"; } public String riskNote() { return "시장 방향과 보유 기간 위험을 확인해야 합니다."; } }
class ThemeAiCategory implements StockCategoryProfile { public String label() { return "AI 테마"; } public String riskNote() { return "기대감과 실제 실적 사이의 차이를 확인해야 합니다."; } }
class ThemeRobotCategory implements StockCategoryProfile { public String label() { return "로봇 테마"; } public String riskNote() { return "수주와 상용화 속도에 따라 변동성이 큽니다."; } }
class ThemeSpaceCategory implements StockCategoryProfile { public String label() { return "우주항공 테마"; } public String riskNote() { return "정책과 장기 투자 사이클을 확인해야 합니다."; } }
class ThemeGreenCategory implements StockCategoryProfile { public String label() { return "친환경 테마"; } public String riskNote() { return "정책 지원과 원가 경쟁력이 중요합니다."; } }
class ThemeDividendCategory implements StockCategoryProfile { public String label() { return "배당 테마"; } public String riskNote() { return "배당 지속성과 이익 안정성을 확인해야 합니다."; } }

class StockCategories {
    private static final List<StockCategoryProfile> CATEGORIES = List.of(
            new SemiconductorCategory(), new BatteryCategory(), new BioCategory(), new AutoCategory(),
            new FinanceCategory(), new InternetCategory(), new GameCategory(), new EntertainmentCategory(),
            new SteelCategory(), new ShipbuildingCategory(), new DefenseCategory(), new ChemicalCategory(),
            new EnergyCategory(), new TelecomCategory(), new RetailCategory(), new CosmeticsCategory(),
            new AviationCategory(), new InsuranceCategory(), new SecuritiesCategory(), new UtilityCategory(),
            new HoldingCompanyCategory(), new FoodCategory(), new ConstructionCategory(), new MachineryCategory(),
            new DisplayCategory(), new ElectronicsCategory(), new MaterialsCategory(), new MedicalCategory(),
            new LogisticsCategory(), new MediaCategory(), new LeisureCategory(), new ReitsCategory(),
            new EtfCategory(), new EtfLeveragedCategory(), new EtfInverseCategory(), new ThemeAiCategory(),
            new ThemeRobotCategory(), new ThemeSpaceCategory(), new ThemeGreenCategory(), new ThemeDividendCategory()
    );

    static String riskNoteFor(String sector) {
        return CATEGORIES.stream()
                .filter(category -> sector.contains(category.label()) || category.label().contains(sector))
                .findFirst()
                .map(category -> "업종 체크: " + category.riskNote())
                .orElse("");
    }
}
