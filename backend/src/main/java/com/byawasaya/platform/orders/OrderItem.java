package com.byawasaya.platform.orders;
import java.math.BigDecimal; import java.util.UUID; import jakarta.persistence.Column; import jakarta.persistence.Entity; import jakarta.persistence.FetchType; import jakarta.persistence.GeneratedValue; import jakarta.persistence.Id; import jakarta.persistence.JoinColumn; import jakarta.persistence.ManyToOne; import jakarta.persistence.Table;
@Entity @Table(name="order_items")
public class OrderItem {
 @Id @GeneratedValue private UUID id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id",nullable=false) private CustomerOrder order;
 @Column(name="product_id",nullable=false) private UUID productId;
 @Column(name="variant_id") private UUID variantId;
 @Column(name="variant_label",length=140) private String variantLabel;
 @Column(nullable=false,length=140) private String productName;
 @Column(length=80) private String sku;
 @Column(nullable=false) private int quantity;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal unitPrice;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal lineTotal;
 protected OrderItem(){}
 public OrderItem(UUID productId,UUID variantId,String variantLabel,String productName,String sku,int quantity,BigDecimal unitPrice,BigDecimal lineTotal){this.productId=productId;this.variantId=variantId;this.variantLabel=variantLabel;this.productName=productName;this.sku=sku;this.quantity=quantity;this.unitPrice=unitPrice;this.lineTotal=lineTotal;}
 void setOrder(CustomerOrder order){this.order=order;} public UUID getId(){return id;} public UUID getProductId(){return productId;} public UUID getVariantId(){return variantId;} public String getVariantLabel(){return variantLabel;} public String getProductName(){return productName;} public String getSku(){return sku;} public int getQuantity(){return quantity;} public BigDecimal getUnitPrice(){return unitPrice;} public BigDecimal getLineTotal(){return lineTotal;}
}
