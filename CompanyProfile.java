abstract class CompanyProfile {
    final String companyName;
    final String sector;

    CompanyProfile(String companyName, String sector) {
        this.companyName = companyName;
        this.sector = sector;
    }

    String summary() {
        return companyName + "은(는) " + sector + " 업종으로 분류됩니다.";
    }
}
