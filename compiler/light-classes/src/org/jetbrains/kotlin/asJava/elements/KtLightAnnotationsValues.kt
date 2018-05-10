/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.elements

import com.intellij.psi.*
import com.intellij.psi.impl.ResolveScopeManager
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KtLightPsiArrayInitializerMemberValue(
    val ktOrigin: KtElement,
    val lightParent: PsiElement,
    val arguments: List<PsiAnnotationMemberValue>
) : PsiElement by ktOrigin, PsiArrayInitializerMemberValue {
    override fun getInitializers(): Array<PsiAnnotationMemberValue> = arguments.toTypedArray()

    override fun getParent(): PsiElement = lightParent

    override fun isPhysical(): Boolean = false
}

class KtLightPsiLiteral(
    val ktOrigin: KtExpression,
    val lightParent: PsiElement
) : PsiElement by ktOrigin, PsiLiteralExpression {

    //TODO: maybe some evaluator?
    override fun getValue(): Any? = when (ktOrigin) {
        is KtStringTemplateExpression -> (ktOrigin.entries.single() as KtLiteralStringTemplateEntry).text // TODO: maybe not single
        else -> ktOrigin.text
    }

    override fun getType(): PsiType? {
        val manager = manager
        val resolveScope = ResolveScopeManager.getElementResolveScope(this)
        return PsiType.getJavaLangString(manager, resolveScope)
    }

    override fun getParent(): PsiElement = lightParent

    override fun isPhysical(): Boolean = false
}