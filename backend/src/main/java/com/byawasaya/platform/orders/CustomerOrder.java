package com.byawasaya.platform.orders;

import java.math.BigDecimal; 
import java.time.Instant; 
import java.util.ArrayList; 
import java.util.List; 
import java.util.UUID;
import jakarta.persistence.CascadeType; 
import jakarta.persistence.Column; 
import jakarta.persistence.Entity; 
import jakarta.persistence.EnumType; 
import jakarta.persistence.Enumerated; 
import jakarta.persistence.GeneratedValue; 
import jakarta.persistence.Id; 
import jakarta.persistence.OneToMany; 
import jakarta.persistence.PrePersist; 
import jakarta.persistence.Table; 
import jakarta.persistence.UniqueConstraint;

@Entity @Table(name="customer_orders", uniqueConstraints=@UniqueConstraint(name="uk_order_number", columnNames="order_number"))
public class CustomerOrder {
 @Id @GeneratedValue private UUID id;
 @Column(name="business_id",nullable=false) private UUID businessId;
 @Column(name="customer_account_id") private UUID customerAccountId;
 @Column(name="order_number",nullable=false,length=30) private String orderNumber;
 @Column(nullable=false,length=120) private String customerName;
 @Column(nullable=false,length=30) private String phone;
 @Column(length=180) private String email;
 @Column(nullable=false,length=100) private String province;
 @Column(nullable=false,length=100) private String district;
 @Column(length=120) private String municipality;
 @Column(length=10) private String ward;
 @Column(nullable=false,length=300) private String addressLine;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private com.byawasaya.platform.catalog.SalesType salesType;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=25) private PaymentMethod paymentMethod;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=25) private PaymentStatus paymentStatus;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=25) private OrderStatus status;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal subtotal;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal deliveryFee;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal advanceAmount;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal totalAmount;
 @Column(length=500) private String customerNote;
 @Column(name="idempotency_key",length=120) private String idempotencyKey;
 @Column(name="idempotency_hash",length=64) private String idempotencyHash;
 @Column(nullable=false,updatable=false) private Instant createdAt;
 @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true) private List<OrderItem> items=new ArrayList<>();
 protected CustomerOrder(){}
 public CustomerOrder(UUID businessId,String orderNumber,String customerName,String phone,String email,String province,String district,String municipality,String ward,String addressLine,com.byawasaya.platform.catalog.SalesType salesType,PaymentMethod paymentMethod,PaymentStatus paymentStatus,OrderStatus status,BigDecimal subtotal,BigDecimal deliveryFee,BigDecimal advanceAmount,BigDecimal totalAmount,String customerNote){
  this.businessId=businessId;this.orderNumber=orderNumber;this.customerName=customerName;this.phone=phone;this.email=email;this.province=province;this.district=district;this.municipality=municipality;this.ward=ward;this.addressLine=addressLine;this.salesType=salesType;this.paymentMethod=paymentMethod;this.paymentStatus=paymentStatus;this.status=status;this.subtotal=subtotal;this.deliveryFee=deliveryFee;this.advanceAmount=advanceAmount;this.totalAmount=totalAmount;this.customerNote=customerNote;
 }
 public void addItem(OrderItem item){items.add(item);item.setOrder(this);}
 @PrePersist void created(){if(createdAt==null)createdAt=Instant.now();}
 public UUID getId(){return id;} public UUID getBusinessId(){return businessId;} public UUID getCustomerAccountId(){return customerAccountId;} public void setCustomerAccountId(UUID id){this.customerAccountId=id;} public String getOrderNumber(){return orderNumber;} public String getCustomerName(){return customerName;} public String getPhone(){return phone;} public String getEmail(){return email;} public String getProvince(){return province;} public String getDistrict(){return district;} public String getMunicipality(){return municipality;} public String getWard(){return ward;} public String getAddressLine(){return addressLine;} public com.byawasaya.platform.catalog.SalesType getSalesType(){return salesType;} public PaymentMethod getPaymentMethod(){return paymentMethod;} public PaymentStatus getPaymentStatus(){return paymentStatus;} public OrderStatus getStatus(){return status;} public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getDeliveryFee(){return deliveryFee;} public BigDecimal getAdvanceAmount(){return advanceAmount;} public BigDecimal getTotalAmount(){return totalAmount;} public String getCustomerNote(){return customerNote;} public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String value){this.idempotencyKey=value;} public String getIdempotencyHash(){return idempotencyHash;} public void setIdempotencyHash(String value){this.idempotencyHash=value;} public Instant getCreatedAt(){return createdAt;} public List<OrderItem> getItems(){return items;}
 public void setStatus(OrderStatus status){this.status=status;} public void setPaymentStatus(PaymentStatus status){this.paymentStatus=status;}
}
