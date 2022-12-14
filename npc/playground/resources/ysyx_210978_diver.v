//////////////////////////////////////////////////////////////////////////////////
// Company: 
// Engineer: 
// 
// Create Date: 08/12/2022 12:05:04 PM
// Design Name: 
// Module Name: divider
// Project Name: 
// Target Devices: 
// Tool Versions: 
// Description: 
// 
// Dependencies: 
// 
// Revision:
// Revision 0.01 - File Created
// Additional Comments:
// 
//////////////////////////////////////////////////////////////////////////////////


module ysyx_210978_diver(
    input                   clock       ,
    input                   reset       ,// high active
    input                   flush       ,// 为高表示取消乘法
    input                   in_valid   ,// 为高表示输入的数据有效，如果没有新的乘法输入，在乘法被接受的下一个周期要置低
    input                   divw        ,// 为高表示是 32 位乘法
    input                   div_signed  ,// 表示是不是有符号除法，为高表示是有符号除法
    input     [63 : 0]      dividend    ,// 被除数，xlen 表示除法器位数
    input     [63 : 0]      divisor     ,// 除数

    output                  out_ready   ,// 高表示乘法器准备好，表示可以输入数据
    output                  out_valid   ,// 高表示乘法器输出的结果有效
    output    [63 : 0]      quotient    ,// 商
    output    [63 : 0]      remainder    // 余数
  );



    reg [6:0] cnt;
    always @(posedge clock) begin
        if(reset) cnt <= 'd63;
        else if(cnt == 'd0) cnt <= 'd63;
        else if(valid) cnt <= 'd63;
        else if(in_valid | busy) cnt <= cnt - 'd1;
        else cnt <= 'd63;
    end

    // 符号位缓存
    reg dsor_neg, dend_neg, div_signed_1;
    always @(posedge clock) begin
        if(reset)begin
            dsor_neg <= 1'b1;
            dend_neg <= 1'b1;
            div_signed_1 <= 1'b0;
        end else if(in_valid & ready)begin
            if(div_signed)begin
                dsor_neg <= divisor[63];
                dend_neg <= dividend[63];
                div_signed_1 <= 1'b1;
            end else begin
                dsor_neg <= 1'b0;
                dend_neg <= 1'b0;
                div_signed_1 <= 1'b0;
            end
        end else begin
            dsor_neg <= dsor_neg;
            dend_neg <= dend_neg;
            div_signed_1 <= div_signed_1;
        end
    end
    // 输入除数被除数绝对值计算
    wire [63:0] dividend_0;
    wire [63:0] divisor_0;
    assign dividend_0 = dividend[63] & div_signed ? (~dividend + 64'b1) : dividend;
    assign divisor_0  = divisor [63] & div_signed ? (~divisor + 64'b1) : divisor;
    //除数缓存
    reg  [64 : 0] divisor_1;
    wire [64 : 0] divisor_2;
    always @(posedge clock) begin
        if(reset) divisor_1 <= 'h0;
        else if(in_valid & ready) divisor_1 <= {1'b0, divisor_0};
        else if(finish) divisor_1 <= 'h0;
        else divisor_1 <= divisor_1;
    end
    assign divisor_2 = (in_valid==1'b1 && ready==1'b1) ? {1'b0, divisor_0} : divisor_1;
    //试商法,减数
    wire [64:0] subor;
    assign subor = divisor_2[64:0];
    wire [64:0] subend;
    assign subend = (in_valid & ready) ? {64'h0, dividend_0[63]} : dividend_1[127:63];
    wire [64:0] subres;
    assign subres = subend - subor;
    wire subneg;
    assign subneg = subres[64];

    //被除数,商,余数逻辑
    reg [191 : 0] dividend_1;
    always @(posedge clock) begin
        if(reset) dividend_1 <= 'h0;
        else if(in_valid & ready)begin
            if(subneg) dividend_1 <= ({128'h0, dividend_0})<<1;//64 + 64 + 64
            else        dividend_1 <= ({64'h0, subres, dividend_0[62:0]})<<1;
        end else if(valid)begin
            dividend_1 <= 'h0;
        end else if(busy)begin
            if(subneg) dividend_1 <= dividend_1 << 1;
            else        dividend_1 <= ({dividend_1[191:128], subres, dividend_1[62:0]})<<1;
        end else begin
            dividend_1 <= dividend_1;
        end
    end

    reg [63:0] q;
    always @(posedge clock) begin
        if(reset)begin
            q <= 64'h0;
        end else if(in_valid & ready)begin
            if(subneg) q[cnt[5:0]] <= 1'b0;
            else q[cnt[5:0]] <= 1'b1;
        end else if(busy)begin
            if(subneg) q[cnt[5:0]] <= 1'b0;
            else q[cnt[5:0]] <= 1'b1;
        end else if(valid)begin
            q <= 64'h0;
        end else begin
            q <= q;
        end
    end

    /* control signal */
    reg  ready, busy;
    always @(posedge clock) begin
        if(reset)begin
            ready <= 1'b1;
        end else if(finish)begin
            ready <= 1'b1;
        end else if (in_valid)begin
            ready <= 1'b0;
        end else begin
            ready <= ready;
        end
    end
    always @(posedge clock) begin
        if(reset)begin
            busy <= 1'b0;
        end else if(finish)begin
            busy <= 1'b0;
        end else if(in_valid)begin
            busy <= 1'b1;
        end else begin
            busy <= busy;
        end
    end

    wire finish;
    assign finish = cnt == 'd0;
    
    /* output */
    reg valid;
    always @(posedge clock) begin
        if(reset)   valid <= 1'b0;
        else if(cnt == 'd0) valid <= 1'b1;
        else valid <= 1'b0;
    end
    assign out_ready = ready;
    assign out_valid = valid;

    wire [63:0] q_neg;
    wire [63:0] r_neg;
    wire [63:0] q_pos;
    wire [63:0] r_pos;
    assign q_pos = q;
    assign r_pos = dividend_1[127:64];
    assign q_neg = (~q_pos) + 1'b1;
    assign r_neg = (~r_pos) + 1'b1;

    
    wire [63:0] q_res;
    wire [63:0] r_res;
    assign q_res = dend_neg ^ dsor_neg ? q_neg : q_pos;
    assign r_res = dend_neg == 1'b1    ? r_neg : r_pos;

    assign remainder = valid ? r_res : 'd0;
    assign quotient  = valid ? q_res : 'd0;

endmodule

