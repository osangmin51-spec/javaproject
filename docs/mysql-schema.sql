create database if not exists mock_stock
    default character set utf8mb4
    collate utf8mb4_unicode_ci;

use mock_stock;

create table if not exists members (
    uid bigint primary key,
    name varchar(100) not null,
    balance int not null
);

create table if not exists shares (
    member_uid bigint not null,
    stock_name varchar(120) not null,
    quantity int not null,
    purchase_price int not null,
    primary key (member_uid, stock_name),
    constraint fk_shares_member
        foreign key (member_uid) references members(uid)
        on delete cascade
);

create table if not exists trade_logs (
    id bigint primary key auto_increment,
    member_uid bigint not null,
    stock_name varchar(120) not null,
    quantity int not null,
    price int not null,
    trade_type varchar(20) not null,
    traded_at datetime not null,
    index idx_trade_logs_member_time (member_uid, traded_at),
    constraint fk_trade_logs_member
        foreign key (member_uid) references members(uid)
        on delete cascade
);
