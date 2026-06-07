abstract class CompanyProfile {
    final String companyName;
    final String sector;

    CompanyProfile(String companyName, String sector) {
        this.companyName = companyName;
        this.sector = sector;
    }
}

interface NewsKeywordProfile {
    String keyword();
}
