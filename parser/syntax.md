\begin{split}
e ::= \
&\ lit \\ 
&\mid uop \ e \\ 
&\mid e \ bop \ e \\
&\mid x \\
&\mid e (e_1, \cdots, e_n) \\
&\mid \mathrm{if} \ e \ \mathrm{then} \ e \ \mathrm{else} \ e \\
&\mid C_i(e_1, \dots ,e_{n_i}) \\
&\mid e \ \mathrm{match} \\
&\quad (\mathrm{case} \ C_i(x_1, \dots, x_{n_i}) \Rightarrow e)^+ \\
&\mid (x_1 \colon \, \tau_1, \cdots, x_n \colon \, \tau_n) \Rightarrow e \\
&\mid (e_1, \cdots, e_n) \\ 
%
lit ::= \
&\ \mathrm{INTEGER} \mid \mathrm{DOUBLE} \mid \mathrm{STRING} \mid \mathrm{true} \mid \mathrm{false} \mid \mathrm{Nil} \mid prim \\
%
prim ::= \
&\ \mathrm{map} \mid \mathrm{filter} \mid \mathrm{foldLeft} \mid \mathrm{foldRight} \\
%
uop ::= \
&\ - \mid \ ! \\
%
bop ::= \
&\ + \mid - \mid * \mid / \\
&\mid \mathrm{<} \mid \mathrm{>} \mid \mathrm{<=} \mid \mathrm{>=} \mid \mathrm{==} \mid \mathrm{!=} \\
&\mid \mathrm{\&\&} \mid \mathrm{||} \\
&\mid \mathrm{::} \\
%
declaration ::= \
&\ \mathrm{val} \ x = e \\
&\mid \mathrm{type} \ T = \tau \\
&\mid \mathrm{def} \ f(x_1 \colon \, \tau_1, \cdots, x_n \colon \, \tau_n) \colon \, \tau = e \\
&\mid \mathrm{def} \ f[\alpha_1, \cdots, \alpha_m](x_1 \colon \, \tau_1, \cdots, x_n \colon \, \tau_n) \colon \, \tau = e\\
&\mid \mathrm{enum} \ T[\alpha_1, \cdots, \alpha_m] \colon   \\
&\quad (\mathrm{case} \ C_i(x_1 \colon \, \tau_1, \dots , x_{n_i} \colon \, \tau_{n_i}))^+\\
%
\tau ::= \
&\ \mathrm{Int} \mid \mathrm{Double} \mid \mathrm{String} \mid \mathrm{Bool} \mid \mathrm{List} [\tau] \mid \tau \to \tau \mid (\tau_1, \cdots, \tau_n) \mid T \mid \alpha
\end{split}