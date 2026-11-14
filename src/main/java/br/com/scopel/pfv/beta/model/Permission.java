package br.com.scopel.pfv.beta.model;

public enum Permission {
    REGISTER_PRODUCTS("Cadastrar produtos"),
    SEE_COST_FIELDS("Ver custo, margem e estoque mínimo no cadastro"),
    EDIT_PRODUCT_DATA("Editar dados dos produtos"),
    EDIT_PRICES("Alterar preços"),
    EDIT_STOCK_QUANTITY("Alterar quantidade em estoque");

    private final String label;

    Permission(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
